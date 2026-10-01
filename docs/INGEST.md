# Ingest: Slack links, video URLs and recordings

`brain add` turns a source that is not text into a text file, `sources/<id>.md`.
After that nothing is special: `/learn` writes notes from the transcript, and `brain verify`
verifies each note's quote against it with the same string comparison as any other file.

```
Slack link ─────┐
Video URL ──────┼─► brain add ─► sources/<id>.md ─► /learn ─► review ─► ask
Recording file ─┘     (local)         one line per segment, timestamped
```

## Prerequisites

| For | You need |
|---|---|
| Video URL | `yt-dlp` and `ffmpeg` (`brew install yt-dlp ffmpeg`) |
| Recording file | `ffmpeg` |
| Speech to text (both) | a local backend: `pip install mlx-whisper` (Apple Silicon) or `pip install faster-whisper`. First run downloads the model once, then it is offline. |
| `--screen` (text shown on screen) | `tesseract` (`brew install tesseract`) |
| Slack link | a Slack token, below |

`BRAIN_PYTHON` picks the Python (default `python3`), `BRAIN_WHISPER_MODEL` the model,
`BRAIN_MAX_MINUTES` the longest video accepted (default 240).

## Video URL

```bash
brain add "https://www.youtube.com/watch?v=jNQXAC9IVRw"
# Downloading "Me at the zoo" (0 min)...  Transcribing locally...
# 4 lines -> sources/video-4b0f48e4f4.md
```

Any site `yt-dlp` supports. The audio is downloaded and transcribed on your machine.

```
# Transcript: Me at the zoo
Source: https://www.youtube.com/watch?v=jNQXAC9IVRw
...
[00:00:00] Alright, so here we are, one of the elephants.
[00:00:04] The cool thing about these guys is that they have really, really, really long trunks.
```

## Screen recording (or any recording file)

```bash
brain add ~/Movies/standup-oct-3.mov            # what was said
brain add ~/Movies/standup-oct-3.mov --screen   # what was said, plus text visible on screen
```

`--screen` samples a frame every 10 seconds (at most 360, so the first hour), reads the text in
it with OCR, and keeps a line only when the screen changed:

```
[00:00:00] We decided to drop the graph database on September 26th because two frameworks would double the work.
[00:00:00] (on screen) Decision log: drop the graph database Owner approved on 26 September
[00:00:06] The owner approved the change.
```

It reads **text**. It does not understand charts, UI state or images.

## Slack link

Slack does not let anyone read a message from its link alone. The supported way is its Web API
with a token you provide:

1. Create a Slack app in your workspace (api.slack.com/apps), add the user-token scopes
   `channels:history`, `groups:history` (private channels) and `users:read`, install it, and
   copy the user token (`xoxp-...`). A user token reads what you can already read. A bot token
   works only in channels the bot was invited to.
2. `export SLACK_TOKEN=xoxp-...`
3. Copy a message link (message menu → Copy link) and run:

```bash
brain add "https://yourteam.slack.com/archives/C0123ABCD/p1727700000123456"
# 7 lines -> sources/slack-c0123abcd-1727700000123456.md
```

The whole thread is read, one line per message: `[2026-09-30 14:03] Ana: Drop the graph db…`.
The token is only ever sent to `https://slack.com/api/`, never to the host in the link.
Not read: files, canvases, huddles, edits history, DMs you cannot already see.

## Then capture, verify, ask

```
/learn sources/video-4b0f48e4f4.md zoo-demo
brain review zoo-demo
brain ask --project zoo-demo "What did the speaker say about elephants?"
```

A note quotes inside **one line** and may point at the moment: `source: file:<id>.md#t=00:00:04`.
The `#t=` part is only a pointer for a human to find the spot; the check reads the file.
Real run, a note quoting a recording:

```
$ brain check            # a misquote is rejected
FAIL  bad-note   quote not found in file:<id>.md#t=00:00:06: "the owner rejected the change"
$ brain ask "Why was the graph database dropped?"
The graph database was dropped because using two frameworks would double the work [1].
  [1] demo/Graph database dropped — current, 2026-09-26 → now
      "because two frameworks would double the work"      file:<id>.md#t=00:00:00
```

## What is checked, and what is not

- **Checked:** every note's quote occurs in the transcript.
- **Not checked:** that the transcript is what was said. Speech to text mishears names and
  numbers, and can invent words over silence. The timestamp is there so a person can listen to
  that second. For decisions that matter, do.
- **Not private by default in one step:** `brain add` runs locally, but `/learn` sends the
  transcript to Claude Code to write notes. For sensitive recordings wait for local extraction
  (roadmap v0.3), or do not capture them. your brain folder is its own private repository; do not publish it
  if it holds private material.
- **Text in a source is data.** A video or message that says "ignore your instructions" is
  quoted as data; the transcript says so at the top and `/learn` is told the same.

## Known limits

- **Slack was tested against a mock Slack server, not a live workspace.** The parser, paging,
  error hints and token handling have unit tests; a live run needs your token. Please report
  what breaks.
- Videos that need a login (private Loom, Teams or Zoom recordings) fail; there is no cookie
  support, on purpose, until it can be done safely. Download the file and add the file.
- Only the Apple Silicon `mlx-whisper` backend was run here. `faster-whisper` and `openai-whisper`
  are supported by `bin/transcribe.py` but untested.
- No speaker names in recordings (no diarization). Slack lines have names.
- One video or thread per command; no playlists, no channel history.
