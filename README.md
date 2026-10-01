# second-brain

### Ask *why* a project is the way it is. Get the answer with a quote you can verify.

![How second-brain works](docs/architecture.svg)

Months later nobody remembers why a decision was made. The reason is buried in a commit message,
a Slack thread, a meeting recording. An AI asked about it can't see it, or answers confidently
and wrongly.

**second-brain reads those sources and keeps small notes. A note only counts if its quote is
found, word for word, in the source.** Then you ask questions in plain English, and every answer
shows its proof, or says "not in the notes".

## It is three steps

| Step | You do | What happens |
|---|---|---|
| **1. Add** | `brain add <video-url \| recording \| slack-link>` *(a repo or text file needs no add)* | the video or thread becomes a text file with timestamps |
| **2. Learn, then review** | In Claude Code: `/learn <what> <topic>`, then in the terminal `brain review <topic>` | Claude drafts notes; **you** read them and accept; a program refuses any note whose quote isn't in the source |
| **3. Ask** | `brain ask "why did we drop the graph database?"` | answer + the quote + where it came from, or `NOT_IN_THE_NOTES` |

## Try it now (2 minutes): the project explains itself

`examples/self-demo` is a ready-made brain **about this very project**: 16 notes, every quote
checked against [`docs/DECISIONS.md`](docs/DECISIONS.md). Real output:

```text
$ cd examples/self-demo

$ brain verify                      # no AI involved, just a string comparison
16 notes (11 current, 5 superseded): every quote verified against its source

$ brain ask "Why is there no Spring Boot in version 0.1?"
Version 0.1 uses plain Java instead of Spring Boot because the project initially aimed for a
CLI tool that does not require a web framework [1]. The earlier plan to use Spring Boot with
LangChain4j was replaced by plain Java on the same day [2].

Sources (printed from the notes, not by the model):
  [1] No Spring in v0.1 — current, 2026-10-01 → now
      "A local CLI and MCP tool does not need a web framework."        file:docs/DECISIONS.md
  [2] Spring Boot plus LangChain4j plan dropped — NO LONGER TRUE, 2026-10-01 → 2026-10-01
      "Supersedes: "Spring Boot + LangChain4j" (same day, earlier)"    file:docs/DECISIONS.md

$ brain ask "Which cloud provider does it run on in production?"
NOT_IN_THE_NOTES                    # a gap is reported, not invented
```

What this shows: an answer you can check in seconds; a replaced decision marked **no longer
true** next to what replaced it; a refusal where the notes are silent. (The first answer is a bit
loose, "initially aimed", which is exactly why the quote is printed beside it.)

**A recording works the same way.** A 9-second screen recording went through `brain add --screen`:

```text
[00:00:00] We decided to drop the graph database on September 26th because two frameworks would double the work.
[00:00:00] (on screen) Decision log: drop the graph database Owner approved on 26 September
```

A note quoting it is accepted. A note that misquotes it is **rejected**:
`quote not found: "the owner rejected the change"`.

## Make your own brain (5 minutes)

```bash
brain init my-brain && cd my-brain      # a new folder, yours, private
```

You get exactly this, and nothing else to learn:

```text
my-brain/
  notes/      the brain itself: small notes, one fact each (you never edit these by hand)
  sources/    text copies of the videos, recordings and chats you added
  README.md   the three steps again
```

Then open Claude Code in `my-brain` and run `/learn ../my-project my-project`, read the drafts with
`brain review my-project`, and ask. Your notes stay in **your** folder; this repository is only
the tool.

## What can go in

| Source | Works? |
|---|---|
| Git repository: history, README, docs, **reasons hidden in commit bodies** | ✅ |
| Any text or Markdown file, a transcript, a chat pasted into a file | ✅ |
| **Video URL** (YouTube and other `yt-dlp` sites): transcribed on your machine | ✅ |
| **Screen recording, or any old video or audio file**: speech *and* on-screen text | ✅ |
| **Slack thread link**, with your own Slack token *(tested against a mock Slack, not yet a live one)* | ✅ |
| PDF, Word, Google Docs, images, web pages, email, Confluence, Jira | not yet |
| Videos behind a login, Slack files and huddles | not yet |

Setup for video and Slack, a real run, and limits: [docs/INGEST.md](docs/INGEST.md).

## Install

You need **JDK 27**. For `ask`, a local model server such as [LM Studio](https://lmstudio.ai) on
`:1234` with `qwen/qwen3-14b` and `text-embedding-nomic-embed-text-v1.5` loaded. For video:
`brew install yt-dlp ffmpeg tesseract` and `pip install mlx-whisper`.

```bash
git clone https://github.com/hhovhann/second-brain && cd second-brain
export JAVA_HOME=/path/to/jdk-27
./gradlew installDist
export PATH="$PWD/bin:$PATH"       # now `brain` works from any folder
brain doctor                       # checks Java and the model server
```

## How to ask Claude Code to use it

Claude Code **does not use second-brain on its own yet**; it uses it when you ask. Easiest first:

1. **In your brain folder**, use the commands: `/ask why did we drop the graph database?` and `/learn <what> <topic>`.
2. **From any other project**, say it: *"Run `brain ask --topic my-project "why did we remove the page graph?"` (from `~/my-brain`) and show me the quote. If it says NOT_IN_THE_NOTES, tell me the reason was never recorded."*
3. **Make it a habit**: paste into that project's `CLAUDE.md`:
   ```markdown
   For "why was this decided / when / what changed" questions, first run, from ~/my-brain:
   brain ask --topic my-project "<question>". Quote what it prints. If it says
   NOT_IN_THE_NOTES, say it is not recorded; do not guess.
   ```

An MCP server would let Claude find the brain and use it **automatically**. It is the next big item (below).

## Why not just ask Claude Code or Codex?

For one repo and a question the code answers, you often should. second-brain adds what they don't
give you: **memory between sessions, one brain across projects, knowledge from meetings, videos
and chats, a machine-checked quote for every claim, "what was true on this date", and answers that
run on your own machine.** Whether it answers *better* is the first thing to measure; the test and
its fixed pass/fail rule are in [docs/BENCHMARK.md](docs/BENCHMARK.md).

## This repository, folder by folder

| Folder | What it is |
|---|---|
| `src/` | the Java program and its tests |
| `bin/` | the `brain` command, and the speech-to-text helper |
| `starter/` | what `brain init` copies into a new brain (its README, rules and `/learn`, `/ask`) |
| `examples/self-demo/` | the ready brain you can try |
| `docs/` | the details: [architecture](docs/ARCHITECTURE.md) · [ingest](docs/INGEST.md) · [roadmap and status](docs/ROADMAP.md) · [decisions](docs/DECISIONS.md) · [security](docs/SECURITY.md) · [benchmark](docs/BENCHMARK.md) |

## Status and next steps

**v0.2 prototype**: the core works and is tested (38 tests). It is **not yet measured against
Claude Code alone**. Next, in order:

1. **Run that benchmark**: it decides whether this is worth building further.
2. **MCP server** (read-only): Claude uses the brain automatically.
3. **Local note writing**: so private recordings never go to a cloud model.
4. **Better retrieval**: the demo missed one relevant note because only 6 reach the model.
5. **More sources**: PDF, Word, images, web pages. Then a small web page.

Honest limits, what works and what doesn't: [docs/ROADMAP.md](docs/ROADMAP.md).

## Contributing

Open an issue, or run `/improve` in Claude Code in this repository: it takes the top roadmap item,
writes a failing test first, fixes it, and records the decision. MIT license.
