# second-brain

### Ask *why* a project is the way it is. Get the answer with a **verified quote**, or "not in the notes".

![How second-brain works](docs/architecture.svg)

Months later nobody remembers why a decision was made. The reason is buried in a commit message,
an old chat, a meeting recording. An AI asked about it either can't see it, or answers
confidently and wrongly. **second-brain turns those sources into small notes and only trusts a
note if its quote is found, word for word, in the source.** Then you ask questions in plain English.

> **Status: v0.2 prototype.** The core works and is tested (32 tests). Not built yet: web page,
> MCP server, PDF/Word/image sources. **Not yet measured against Claude Code alone**: see
> [what is proven and what is not](docs/STATUS.md).

---

## What you can do with it

| | Feature | Example |
|---|---|---|
| ✅ | **Ask why, with proof**: every claim shows its quote and source | `brain ask "Why did we drop Spring Boot?"` |
| ✅ | **Honest gaps**: it says `NOT_IN_THE_NOTES` instead of guessing | "Which cloud do we deploy to?" → *not recorded* |
| ✅ | **Time travel**: what was true on a date, and what replaced it | `brain ask --as-of 2026-09-20 "Which frameworks?"` |
| ✅ | **Many projects, one brain** | "Which of my projects use Spring AI, and which stopped?" |
| ✅ | **Git history and docs** (reasons hidden in commit bodies too) | `/capture ../my-repo my-repo` |
| ✅ | **Video URL** (YouTube and more) → local speech-to-text | `brain ingest <url>` |
| ✅ | **Screen recording or any old video/audio file**: speech *and* on-screen text | `brain ingest demo.mov --screen` |
| ✅ | **Slack thread link** (with your own Slack token) | `brain ingest <slack-link>` |
| ✅ | **Private by default**: answers run on a local model, no per-question cost | LM Studio / Ollama |
| 🚧 | Claude Code uses it **by itself** (MCP server) | [roadmap v0.4](docs/ROADMAP.md) |
| 🚧 | PDF, Word, Google Docs, images, web pages, email | [roadmap v0.3](docs/ROADMAP.md) |
| 🚧 | Web page for non-developers | [roadmap v0.5](docs/ROADMAP.md) |

---

## See it work: the self-demo

The project explains **itself**. `examples/brain/` is this repo's own decision log turned into
14 notes. Every quote is checked against [`docs/DECISIONS.md`](docs/DECISIONS.md). Nothing to
configure beyond the install below. These are real outputs:

```text
$ brain check                         # no AI involved: pure string comparison
14 notes (10 current, 4 superseded): every quote verified against its source

$ brain ask "Why is there no Spring Boot in version 0.1?"
Version 0.1 uses plain Java instead of Spring Boot because the project initially aimed for a
CLI tool that does not require a web framework [1]. The earlier plan to use Spring Boot with
LangChain4j was replaced by plain Java on the same day [2].

Sources (printed from the notes, not by the model):
  [1] No Spring in v0.1, LangChain4j core only — current, 2026-10-01 → now
      "A local CLI and MCP tool does not need a web framework."        file:docs/DECISIONS.md
  [2] Spring Boot plus LangChain4j plan dropped — NO LONGER TRUE, 2026-10-01 → 2026-10-01
      "Supersedes: "Spring Boot + LangChain4j" (same day, earlier)"    file:docs/DECISIONS.md

$ brain ask "Which cloud provider does it run on in production?"
NOT_IN_THE_NOTES                      # a gap is reported, not invented
```

**What this demonstrates:** an answer you can verify in seconds (open the file, find the quote);
a replaced decision shown as *no longer true* next to what replaced it; and a refusal where the
notes are silent. The first answer is a little loose ("initially aimed"), which is why the quote
is printed beside it: small local models are not perfect, so you can always check.

### Demo 2: a recording becomes verifiable notes

A 9-second screen recording (narration + a slide) went through `brain ingest --screen`:

```text
[00:00:00] We decided to drop the graph database on September 26th because two frameworks would double the work.
[00:00:00] (on screen) Decision log: drop the graph database Owner approved on 26 September
[00:00:06] The owner approved the change.
```

A note quoting it passes; a note that misquotes it is **rejected**:

```text
FAIL  bad-note   quote not found in file:transcript.md#t=00:00:06: "the owner rejected the change"
```

The same flow works for a YouTube URL (`brain ingest <url>`) and a Slack thread. Details,
setup and limits: [docs/INGEST.md](docs/INGEST.md).

---

## Install and run (5 minutes)

You need **JDK 27**, and for `ask` a local OpenAI-compatible model server such as
[LM Studio](https://lmstudio.ai) on `:1234` with `qwen/qwen3-14b` and
`text-embedding-nomic-embed-text-v1.5` loaded. For video: `brew install yt-dlp ffmpeg tesseract`
and `pip install mlx-whisper` (see [INGEST](docs/INGEST.md)).

```bash
git clone https://github.com/hhovhann/second-brain && cd second-brain
export JAVA_HOME=/path/to/jdk-27              # sdkman users: `sdk env`
./gradlew installDist
export PATH="$PWD/bin:$PATH"                  # now `brain` works from any folder

export BRAIN_DIR=examples/brain               # the bundled self-demo
brain doctor                                  # is Java and the model server OK?
brain check
brain ask "Why did you drop the graph database?"
```

## Use it on your own project

```text
1. Open Claude Code in this folder.
2. /capture ../my-project my-project        → drafts in brain/_draft/my-project/
   (or: brain ingest <video|recording|slack-link>, then /capture sources/<id> my-project)
3. Read the drafts.  brain check --drafts  then  brain promote my-project
4. brain ask --project my-project "Why did we ...?"
```

`brain/`, `sources/` and `demo/` are git-ignored: your real notes and recordings never get
committed here. Keep them in your own private repository.

---

## How to ask Claude Code to use it

Be precise about what is true today: **Claude Code does not use second-brain on its own yet.**
It uses it when you tell it to. Three ways, easiest first:

**1. Inside this folder, use the commands:**
```text
/ask why did we drop the graph database?
/capture ../my-project my-project
```

**2. From any other project, say it in a prompt:**
> Before you answer, run `brain ask --project my-project "why did we remove the page graph?"`
> and show me the quote. If it says NOT_IN_THE_NOTES, tell me the reason was never recorded.

**3. Make it a habit: paste this into that project's `CLAUDE.md`:**
```markdown
## Project memory
For questions about why something was decided, when, or what changed, query the project brain
first: `brain ask --project my-project "<question>"`. Quote what it prints. If it returns
NOT_IN_THE_NOTES, say the history is not recorded; do not guess.
```

To feed it: *"Run `brain ingest ~/Movies/standup.mov --screen`, then `/capture` the result and
list the decisions and who owns what."*

The step after this is an **MCP server**, which lets Claude Code and Codex discover and call the
brain as a tool without being told. It is on the roadmap (v0.4), read-only by design.

## Why not just ask Claude Code or Codex?

For one repo and a question the code answers, you often should. second-brain adds what they
don't give you: **memory between sessions, one brain across projects, knowledge from meetings,
videos and chats, a machine-checked quote for every claim, "what was true on this date", and
answers that run locally.** Whether it actually answers *better* is the first thing to
measure: the test and its fixed pass/fail rule are in [docs/BENCHMARK.md](docs/BENCHMARK.md).

## What to improve next

| Priority | Improvement | Why |
|---|---|---|
| 1 | **Run the benchmark** against Claude Code alone | Proves it is worth using; decides the product |
| 2 | **MCP server** (read-only) | Claude uses the brain automatically, no prompting |
| 3 | **Local note extraction** (`brain add`) | Private recordings no longer pass through a cloud model |
| 4 | Keyword search + more notes per answer | Fixes the missed-note case seen in the demo |
| 5 | PDF / Word / image / web-page sources | "All types of source" |
| 6 | CI and a small web page | Easy for others to trust and try |

Full plan: [docs/ROADMAP.md](docs/ROADMAP.md).

## Documents

| | |
|---|---|
| [Architecture](docs/ARCHITECTURE.md) | the picture above in detail, the note format, how `ask` works |
| [Use cases](docs/USE-CASES.md) | the problem, examples, which inputs work |
| [Ingest](docs/INGEST.md) | Slack links, video URLs, recordings: setup, real run, limits |
| [Roadmap](docs/ROADMAP.md) · [Status](docs/STATUS.md) | what is next · what works and what doesn't |
| [Decisions](docs/DECISIONS.md) · [Security](docs/SECURITY.md) · [Benchmark](docs/BENCHMARK.md) | why it is built this way · threat model · the go/no-go test |

## Contributing

Open an issue, or run `/improve` in Claude Code: it takes the top roadmap item, writes a failing
test first, fixes it, and records the decision. Decisions that replace earlier ones say so in
`docs/DECISIONS.md`; nothing is deleted.

MIT license.
