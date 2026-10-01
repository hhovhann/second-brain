# Inputs: what `brain capture` can read

One command takes all of these:

```bash
brain capture <anything> [topic]
```

| Input | Example | What is read | Needs |
|---|---|---|---|
| Git repository | `brain capture ../my-project` | README, CHANGELOG, `docs/*.md`, and every commit message **with its body** (reasons often live only there) | `git` |
| Folder | `brain capture ~/notes/atlas` | text and Markdown files, PDF, Word, PowerPoint, HTML, subtitles (up to 40 files, 3 levels) | - |
| Text or Markdown file | `brain capture decisions.md` | the file, line by line | - |
| PDF | `brain capture contract.pdf` | the text, page by page (`#page=4` marks where a quote sits) | - (built in) |
| Word | `brain capture spec.docx` | paragraphs, including tables | - (built in) |
| PowerPoint | `brain capture deck.pptx` | slide text **and speaker notes** (`#slide=2`) | - (built in) |
| Image or screenshot | `brain capture whiteboard.png` | text in the image (OCR) | `brew install tesseract` |
| Subtitles | `brain capture talk.vtt` | one timestamped line per cue (`.srt` too) | - |
| Web page | `brain capture https://adr.github.io/` | headings, paragraphs, lists, tables; menus and scripts are dropped | - |
| Google Doc, Slides, Sheet | `brain capture https://docs.google.com/document/d/...` | works if shared as "anyone with the link can view" | - |
| Video link | `brain capture https://www.youtube.com/watch?v=...` | the speech, transcribed on your machine, timestamped | `brew install yt-dlp ffmpeg`, `pip install mlx-whisper` |
| Recording or audio file | `brain capture standup.mov` | the speech, timestamped | `ffmpeg`, `mlx-whisper` |
| Screen recording | `brain capture demo.mov --screen` | the speech **and** text visible on screen, every 10 seconds | plus `tesseract` |
| Slack thread | `brain capture https://team.slack.com/archives/C0123ABCD/p1727700000123456` | the whole thread, one line per message | a Slack token, below |

**Not read yet:** Excel, email (`.eml`, `.msg`), Confluence, Jira, Notion, anything behind a login
(save it as PDF or text and capture the file), Slack files, canvases and huddles. An unsupported file
says what is supported instead of failing quietly.

## What happens, step by step

1. **Read.** Anything that is not plain text or a repo is converted once and saved as
   `sources/<id>.md` in your brain, so the quotes stay checkable even if the original changes or
   moves. Repos are read in place so a note can point at the real commit.
2. **Propose.** The local model reads the text in parts of about 6,000 characters and proposes facts,
   each with an exact quote. This runs on your machine; nothing is sent to a hosted model.
3. **Verify.** Code keeps a proposal only if its quote is found, word for word, in the source. It
   then attaches the source itself: the commit (`git:ae2f723`), the file, and where in it
   (`#t=00:12:03`, `#page=4`, `#slide=2`). The model never writes that.
4. **Accept.** You read each note with its quote and accept them. Drafts wait in `notes/_draft/`
   and are never used to answer questions until you do.

Add `--yes` to accept straight away. Add `--history` to also guess which facts replaced which (off by
default: the guesses are about half right, so a person should check them). Inside Claude Code, `/capture` runs the same command and shows
you the notes before you accept.

## Video, recordings and Slack: setup

```bash
brew install yt-dlp ffmpeg tesseract
pip install mlx-whisper          # Apple Silicon; or: pip install faster-whisper
```

`BRAIN_PYTHON` picks the Python, `BRAIN_WHISPER_MODEL` the speech model (first use downloads it,
then it is offline), `BRAIN_MAX_MINUTES` the longest video accepted (default 240). The audio is
downloaded and transcribed locally; frames are sampled every 10 seconds (at most 360, the first
hour) and a line is kept only when the screen changed.

**Slack** does not let anyone read a message from its link alone. The supported way is its Web API
with a token you provide:

1. Create a Slack app in your workspace (api.slack.com/apps), add the user-token scopes
   `channels:history`, `groups:history` (private channels) and `users:read`, install it, and copy
   the user token (`xoxp-...`). A user token reads what you can already read.
2. `export SLACK_TOKEN=xoxp-...`
3. Copy a message link (message menu, Copy link) and capture it.

The token is only ever sent to `https://slack.com/api/`, never to the host written in the link.

## What is checked, and what is not

- **Checked:** every note's quote occurs in its source, word for word.
- **Not checked:** that the source is what was said or true. Speech-to-text and OCR mishear names and
  numbers, and speech-to-text can invent words over silence. The timestamp or page number is there so
  a person can look or listen. For decisions that matter, do.
- **Text in a source is data.** A web page or message that says "ignore your instructions" is just
  text: proposals only become notes if their quote is in the source, and nothing is trusted until
  you accept it.
- **Private by default.** Reading, speech-to-text, OCR and note writing all run on your machine.
  Network use is limited to fetching the thing you asked for (a video, a web page, a Slack thread).

## Known limits

- **Slack was tested against a mock Slack server, not a live workspace.** The parser, paging,
  error hints and token handling have unit tests; a live run needs your token.
- A 14B local model **misses facts** a person would keep, and writes some notes with a longer quote
  than ideal. On a first real repository it wrote 103 verified notes but picked the same sentence as a
  human for only 6 of 20 key facts. The gate stops wrong notes, not missing ones. See the measured results in
  [ROADMAP.md](ROADMAP.md).
- Videos that need a login (private Loom, Teams or Zoom recordings) fail; there is no cookie
  support, on purpose, until it can be done safely. Download the file and capture the file.
- Only the Apple Silicon `mlx-whisper` backend was run here; `faster-whisper` and `openai-whisper`
  are supported by the helper but untested.
- No speaker names in recordings. One video, page or thread per command; no playlists or channel history.
