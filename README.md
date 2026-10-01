# second-brain

![ci](https://github.com/hhovhann/second-brain/actions/workflows/ci.yml/badge.svg)

### Ask *why* a project is the way it is. Get the answer with a quote you can verify.

![How second-brain works](docs/architecture.svg)

Months later nobody remembers why a decision was made. The reason is buried in a commit message,
a PDF, a Slack thread, a meeting recording. An AI asked about it can't see it, or answers
confidently and wrongly.

**second-brain reads your sources and keeps small notes. A note only counts if its quote is found,
word for word, in the source.** Then you ask in plain English, and every answer shows its proof, or
says "not in the notes". Everything runs on your machine.

## Two commands

```bash
brain capture <anything>                            # put knowledge in
brain ask "why did we drop the graph database?"     # get answers out
```

`<anything>` is a **repo, folder, file, PDF, Word, PowerPoint, image, web page, Google Doc, video link,
screen recording, or Slack link.** Capture reads it, a local model proposes the notes, code checks that
every quote really is in the source (and drops the rest), and you accept what is left. You make your
brain once with `brain init my-brain`.

## Try it now (2 minutes): the project explains itself

`demo/` is a ready-made brain **about this very project**: 18 notes, every quote checked against
[`docs/DECISIONS.md`](docs/DECISIONS.md). Real output:

```text
$ cd demo

$ brain verify                      # no AI involved, just a string comparison
18 notes (13 current, 5 superseded): every quote verified against its source

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

You get an answer you can check in seconds, a replaced decision marked **no longer true** next to
what replaced it, and a refusal where the notes are silent. (The first answer is a bit loose,
"initially aimed", which is exactly why the quote is printed beside it.)

## See capture work

A Word memo went in. This is real output, from the local model, with no Claude involved:

```text
$ brain capture memo.docx atlas
Read memo.docx: 465 characters in 1 part. Finding what is worth keeping (local model)...
4 notes drafted; 0 proposals dropped because the quote was not found in the source.

 1. Dana Whitfield owns the migration to Postgres   [quote verified]
 2. MySQL was rejected for the billing service migration   [quote verified]
 3. Extra database licence cost if usage doubles   [quote verified]
 4. Project Atlas moved billing service from SQLite to Postgres   [quote verified]
      "On 12 March 2026 the steering group decided to move the billing service from SQLite to Postgres."
Add these 4 notes to your brain? [y/N]
```

The same command read a PDF (notes point at `#page=1`), a screenshot (OCR), a web page, and a screen
recording (notes point at the minute). A quote that is **not** in the source never becomes a note.

## What it can read

| | |
|---|---|
| Git repo (history **with commit bodies**, README, docs) or a folder | yes |
| Text, Markdown, subtitles (`.vtt`, `.srt`) | yes |
| PDF, Word (`.docx`), PowerPoint (`.pptx`, **including speaker notes**) | yes |
| Image or screenshot (text is read by OCR) | yes |
| Web page, Google Doc / Slides / Sheet (shared "anyone with the link") | yes |
| Video link (YouTube and more), recording, audio file, **screen recording with on-screen text** | yes |
| Slack message link (with your own Slack token; tested against a mock Slack, not yet a live one) | yes |
| Excel, email, Confluence, Jira, Notion, anything behind a login | not yet |

Setup for video and Slack, a step-by-step of what capture does, and limits: [docs/INPUTS.md](docs/INPUTS.md).

## Make your own brain

```bash
brain init my-brain && cd my-brain
brain capture ../my-project
brain ask "why did we ...?"
```

```text
my-brain/
  notes/      the brain: small notes, one fact each (you never edit these by hand)
  sources/    text copies of what you added, so every quote stays checkable
```

Your notes live in **your** folder (a private git repository made by `brain init`); this repository is
only the tool. Inside Claude Code the same two commands are `/capture` and `/ask`.

## Install

You need **JDK 27** and a local model server such as [LM Studio](https://lmstudio.ai) on `:1234`
with `qwen/qwen3-14b` and `text-embedding-nomic-embed-text-v1.5` loaded. For video and images:
`brew install yt-dlp ffmpeg tesseract` and `pip install mlx-whisper`.

```bash
git clone https://github.com/hhovhann/second-brain && cd second-brain
export JAVA_HOME=/path/to/jdk-27
./gradlew installDist
export PATH="$PWD:$PATH"          # now `brain` works from any folder
brain doctor                      # checks Java and the model server
```

## Let Claude Code use it by itself

second-brain includes a **read-only MCP server**. Once connected, Claude Code searches your brain
*before* answering "why was this decided / when / what changed", without being told to. `brain init`
wires it up: open Claude Code in your brain folder and approve the `brain` server once, or for any
project run the one line `brain init` prints (`claude mcp add --scope user brain ...`).

A real run, in an empty folder, with no mention of the brain in the question:

```text
> Why did the second-brain project drop its graph database? Keep it to two sentences.
  tool call: brain_search {"query": "second-brain project dropped graph database, why"}
  Claude: The earlier prototype's Neo4j + Postgres + Redis stack was replaced by plain Markdown
  files in Git. The recorded reasons are that Postgres and Redis went unused, "a local tool
  should not need Docker," and a few thousand notes fit in memory. The notes don't give a
  separate reason for Neo4j itself, so that part is inferred.
```

Three tools, all read-only; none takes a path or a permission (a test enforces that).

## Why not just ask Claude Code or Codex?

For one repo and a question the code answers, you often should. second-brain adds what they don't
give you: **memory between sessions, one brain across projects, knowledge from meetings, videos,
documents and chats, a machine-checked quote for every claim, "what was true on this date", and
everything local.** Whether it answers *better* is the first thing to measure; the test and its fixed
pass/fail rule are in [docs/BENCHMARK.md](docs/BENCHMARK.md).

## This repository

| | |
|---|---|
| `src/` | the program, its tests, and the files it ships with (starter files, speech-to-text helper) |
| `docs/` | [architecture](docs/ARCHITECTURE.md) · [inputs](docs/INPUTS.md) · [roadmap and status](docs/ROADMAP.md) · [decisions](docs/DECISIONS.md) · [security](docs/SECURITY.md) · [benchmark](docs/BENCHMARK.md) |
| `demo/` | the ready brain you can try |
| `gradle/` | the build tool |
| `brain` | the command (one script) |

## Status and next steps

**v0.3 prototype**: the core works and is tested (CI runs on every push). It is **not yet measured
against Claude Code alone**, and a small local model **misses facts** a person would keep (the check
stops wrong notes, not missing ones). Next, in order:

1. **Run the benchmark** against Claude Code alone: it decides whether this is worth building further.
2. **Measure and raise recall** of the local extractor.
3. **More sources**: Excel, email, Confluence, Jira, Notion.
4. **A small web page** for people who don't use a terminal.

What works, what doesn't, and the measurements: [docs/ROADMAP.md](docs/ROADMAP.md).

## Contributing

Open an issue, or run `/improve` in Claude Code in this repository: it takes the top roadmap item,
writes a failing test first, fixes it, and records the decision. MIT license.
