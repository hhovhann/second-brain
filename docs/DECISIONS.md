# Decisions

Newest last. A decision that replaces an earlier one says so, and the earlier one is marked
superseded — never deleted. This file is also the tool's first demo corpus: it must be able
to answer "why did we drop X, and when?" about itself.

## D1 — Name: `second-brain`, command `brain`
- Date: 2026-10-01 · Status: **current** · Supersedes: the working name `lore`
- `lore` was proposed for brevity and was not self-explanatory. The owner asked for simple and
  clear; `second-brain` says what it is.

## D2 — No database; Markdown in Git is the store
- Date: 2026-10-01 · Status: **current** · Supersedes: Neo4j + Postgres + Redis (an earlier prototype)
- Why: the 2026-06-22 requirement "Markdown is the source of truth"; the earlier prototype's
  Postgres and Redis went unused; a local tool should not need Docker. At a few thousand facts an
  in-memory index built from files answers in milliseconds.
- Risk: the Neo4j version's measured score (9 of 10) does not carry over. M1 must reproduce it
  on the benchmark corpus, or this decision is reversed behind a store interface.

## D3 — Java 27, preview features isolated to `Parallel`
- Date: 2026-10-01 · Status: **current**
- Scoped values (final in 25) carry the reader; virtual threads (final) carry blocking I/O;
  structured concurrency (JEP 533, 7th preview) is used in exactly one class.
- Costs accepted: Java 27 is not an LTS (about six months of updates); preview-compiled classes
  run only on the same JDK, so build and runtime must both be 27. Fallback: Java 25 LTS.

## D4 — No Spring in v0.1
- Date: 2026-10-01 · Status: **current** · Supersedes: "Spring Boot + LangChain4j" (same day, earlier)
- A local CLI and MCP tool does not need a web framework. LangChain4j core only. Spring Boot
  returns if this becomes a server (M4).
- Evidence from an earlier project by the maintainer: Spring AI 2.0.1 ran beside LangChain4j
  from 2026-09-07 and was removed on 2026-09-26 because that track "never went past plain chat"
  and two frameworks "would double every step". Same conclusion here: one framework.
- LangChain4j is used only through `ChatModel`, `EmbeddingModel` and JSON-schema types, as in
  the earlier prototype, so Spring AI could replace it behind one seam.

## D5 — One Gradle module, not three
- Date: 2026-10-01 · Status: **current** · Supersedes: the `core` / `engine` / `app` split proposed earlier
- Split when a seam hurts, not before.

## D6 — No graph hops in v0.1
- Date: 2026-10-01 · Status: **current**
- Evidence: an earlier project added a one-hop page graph and removed it the same day (2026-09-27)
  for simplicity. History lives in typed facts and supersession links,
  which need no traversal engine.

## D7 — Read-only MCP; the reader is fixed at startup
- Date: 2026-10-01 · Status: **current**
- A tool argument is written by a model, possibly from corpus text, so no tool may take
  `grants`, and no tool may write. Same rule as the earlier prototype. A test will assert it.

## D8 — Ship only a Claude Code skill if the benchmark says so
- Date: 2026-10-01 · Status: **current**
- `docs/BENCHMARK.md` states the kill criterion up front, so the result cannot be argued around
  afterwards.

## D9 — Notes first; Claude Code is the capture step, Java is the gate and the recall
- Date: 2026-10-01 · Status: **current** · Supersedes: "Java engine extracts facts" as the first milestone
- Claude Code writes better notes than a 14B local model, and an earlier tool already worked that
  way, so `/capture` produces drafts. Java does what an instruction cannot: verify every quote
  (`check`), gate promotion (`promote`), answer from trusted notes only (`ask`), run evals.
- Model-based extraction (`add`) stays on the backlog for private or bulk material.

## D10 — Retrieval resolves currency
- Date: 2026-10-01 · Status: **current**
- Evidence: the first eval run answered "Java 26" for a question whose current answer was 27,
  because the stale note matched the question best. `Ask.addCurrentVersions` now adds every
  superseded hit's successors. The stage is called "resolve currency".

## D11 — Notes state what each number counts
- Date: 2026-10-01 · Status: **current**
- Evidence: the 14B answer model inverted "(1 of 13 against 13 of 13)" and "gave 0 then 1 of 5"
  twice. Notes are written one measurement per line ("RAG: 13 of 13; file search: 1 of 13").
  A limit of small answer models, not fixed by the prompt alone; a stronger answer model is on the backlog.

## D12 — Drafts are untrusted by construction
- Date: 2026-10-01 · Status: **current**
- `Note.loadAll` skips `_draft/`; only `promote` moves notes out, and only after every quote,
  id and supersession link verifies. This makes review-before-promote mechanical.

## D13 — Your brain is its own folder; three verbs
- Date: 2026-10-01 · Status: **current** · Supersedes: keeping notes inside the tool's repository, and the names capture, ingest, promote and check
- Evidence: a first-time reader could not tell which folders were the tool and which were their
  own data, or what each step was for.
- The tool is installed once. `brain init my-brain` makes a separate folder with `notes/` and
  `sources/` only; drafts wait in `notes/_draft/`. The steps are add, learn, review, ask. The
  old command names still work as aliases.
- Each video, recording or Slack thread becomes one file, `sources/<id>.md`; audio, video and
  frames are temporary and deleted.

## D14 — The MCP server returns notes; the calling model writes the answer
- Date: 2026-10-01 · Status: **current**
- Evidence: in a real run, Claude Code called `brain_search` on its own, without being told the
  brain existed, and answered with the exact quote about Docker that the local 14B answer model had
  left out of its own answer on the same notes.
- `brain_search` returns the best notes with their verified quote, source and validity; `brain_ask`
  (the local model) exists but is secondary. Both are read-only and take no path or permission
  argument (D7); a test asserts the argument names.
- Retrieval now fuses embedding search with exact-word search (BM25 and rank fusion) and passes 8
  notes. Measured on 13 real questions: no regression and no measurable gain. Kept for exact names
  such as versions and tool names, which embeddings can miss.
