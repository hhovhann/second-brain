# Use cases, and what works today

## The problem

Why a decision was made lives in places nobody re-reads: commit bodies, old chats, meeting
recordings. A few months later nobody remembers, and an AI asked about it either has no access
or answers fluently and wrongly. second-brain turns those sources into small notes that each
carry a quote a program has verified, then answers from the notes only.

## What can be an input today

| Input | Works today? | How |
|---|---|---|
| A git repository (history, README, CHANGELOG, docs) | **Yes** | `/capture ../my-repo my-repo`. Reasons that exist only in commit bodies are captured. |
| Any text or Markdown file | **Yes** | The file is the source; quotes are checked against it. |
| A meeting transcript saved as text (`.txt`, `.vtt`) | **Yes** | Same as any text file. Keep the file in a folder the note's `repo:` points to. |
| A chat copied or exported to a text file | **Yes** | Paste it into a file first. |
| A Slack **link** | **No** | A link cannot be read without access. Export or paste the thread into a file. A live connector does not exist. |
| A **video URL** (YouTube, Loom, Teams recording) | **No** | Nothing downloads or transcribes video. Transcribe it yourself, save the text, and it becomes a text file source. A built-in transcription step is planned for v0.3. |
| A **screen recording** | **No** | Only what is *said* could ever become text, and only after transcription. What is *shown* on screen is not read. |
| Code diffs | **Partly** | The commit *message* is checked, the diff is not. |

So: the tool does **not** yet do "chat, video link, screen recording" by itself. It does the
second half of that pipeline (turn text into verified, askable notes). The first half
(get text out of links and recordings) is on the [roadmap](ROADMAP.md).

## Examples of use

**1. Returning to your own project after a month.**
`brain ask --project my-app "Why did we stop using the page graph?"` returns the reason and the
commit it came from, or says the reason was never recorded.

**2. Onboarding a new teammate.**
They ask "why is the code like this?" and get answers with the quote and commit, not a guess.
Review-before-promote means a person has read what the notes say.

**3. Questions across several projects.**
`brain ask "Which of my projects use Spring AI, and which stopped?"` reads all projects' notes,
including facts that are no longer true.

**4. What was true on a given day.**
`brain ask --as-of 2026-09-20 "Which frameworks did it use?"` answers as of that date.

**5. Meeting decisions and commitments.**
Save the transcript as text, `/capture` it, review, promote. Later: "what did we commit to?"
Notes can carry `commitment` and `open_question` types.

**6. Telling you what is not known.**
A question the notes do not cover gets `NOT_IN_THE_NOTES`, which is useful information: it
is a gap to fill with another source.

**7. An agent's memory (planned).** Once the read-only MCP server exists (v0.4), Claude Code
or Codex can query the brain before re-reading everything.

## Why not just ask Claude Code or Codex

For one repository and a question the code answers, you often should. They are strong. The
gaps this project tries to fill are:

| Gap | Claude Code / Codex alone | second-brain |
|---|---|---|
| Memory between sessions | Starts empty, re-reads | Notes persist, answers are cheap |
| Several projects at once | Sees the folder you opened | One brain across projects |
| Knowledge outside code | Cannot see meetings and chats | Transcripts and exports become notes |
| Proof | Fluent, no check | Every claim has a machine-verified quote |
| Time | Mostly the current state | Validity dates and "no longer true" |
| Privacy and cost | Cloud quota per question | Answers run on a local model |

**Not yet shown:** that it answers better than Claude Code on real questions. That benchmark is
the first item of v0.2, with a pass/fail rule fixed in advance in [BENCHMARK.md](BENCHMARK.md).
