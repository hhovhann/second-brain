# Roadmap and status

Where things stand today, then what comes next.

## Where we are today

### What works (verified by running it)

- **Notes**: Markdown files with a verbatim quote, a source, validity dates and replacement links.
- **`verify` / `review`** (formerly check / promote): drafts are untrusted until accepted in `brain review`; it refuses if any quote,
  id or supersession link fails. Path traversal and git-argument injection are rejected (tested).
- **`ask`**: local Qwen3 14B, about 7 s per question, no hosted-model tokens. Cites notes,
  prints sources from the notes themselves, declines with `NOT_IN_THE_NOTES`. Supports
  `--project` and `--as-of`. Adds the replacement of any outdated note it retrieves.
- **`eval`**: runs a gold-question file and writes a report.
- **`init`, `add`, `review`, `ask`**: your brain is its own folder; `/learn` and `/ask` Claude Code commands are copied into it from `starter/`.
- **Self-demo**: `examples/self-demo/` is a ready brain with 16 notes about this project, all verified against
  `docs/DECISIONS.md`; `eval.yaml` has 5 questions (5 of 5 pass on the automatic check).
- **`add`**: video URL (yt-dlp, local Whisper), recording file, `--screen` (OCR of on-screen
  text), Slack thread link (own token). Video URL and screen recording were run for real; the
  Slack path is covered by unit tests against a mock server only. See `docs/INGEST.md`.
- 38 unit tests.

### Measured, and how far to trust it

On a private project (13 gold questions): 11 right on the first run, 13 right after two
retrieval/prompt fixes and two note rewrites, so two of those are not blind passes. The notes and
the answer key were written by the same agent. **Trust level: low to medium.** It shows the
design works; it does not show it beats Claude Code alone. That benchmark
([BENCHMARK.md](BENCHMARK.md)) has **not been run**.

### Known problems

- A small answer model swaps ambiguous numbers; notes must state one measurement per line.
- Only the 6 nearest notes reach the model, so a relevant note can be missed (seen in the
  self-demo: the "why no database" note exists but the answer to "why drop the graph database?"
  did not cite it).
- Speech-to-text mishears names and numbers; the quote check cannot catch that (only a person
  listening at the timestamp can).
- `/learn` sends transcripts to Claude Code, so private recordings are not private end to end.
- Notes were once lost from an uncommitted folder during development and the cause was never
  found. Commit your notes (or keep them in a private repository) as soon as you have them.

### Not built

Model-based extraction (`brain add`), speaker names, login-gated videos, Slack history and files,
MCP server, web UI, keyword search, access control, injection evals, watch mode, deployment. The README must
never claim these. Order and plan: [ROADMAP.md](ROADMAP.md).

---

## What comes next
Order matters: **prove it, then widen the inputs, then widen the users.** Nothing below is
built unless it says "done". To propose or request a change, open an issue, or run `/improve`
in Claude Code, which takes the top open item, writes a failing test first, and records the result.

## v0.1 — the verified core (done)

- `verify`, `review`, `ask` (`--topic`, `--as-of`), `eval`, `doctor`
- Notes with validity dates and supersession; retrieval that resolves currency
- Claude Code commands: `/learn`, `/ask` (in your brain), `/improve` (in this repo)
- A self-demo: the project's own decision log as a brain (`examples/self-demo`)

## v0.1.1 — non-text inputs (done)

- `brain add` for video URLs, recordings (speech, and on-screen text with `--screen`) and Slack
  thread links, producing timestamped transcripts that the existing quote check verifies
- Live Slack run still to be confirmed with a real workspace

## v0.2 — prove it is worth using

The benchmark in [BENCHMARK.md](BENCHMARK.md) has a fixed rule: if Claude Code alone is within
one question of this tool on the history questions, the right product is a Claude Code skill, not
a program. So this comes first.

- [ ] Run the benchmark: Claude Code alone vs a knowledge-graph tool vs second-brain, three runs each
- [ ] Keyword search next to vectors (embeddings miss exact names), fused by rank
- [ ] Claim-support gate: refuse an answer whose subject appears in no retrieved note
- [ ] Prompt-injection evals (`injection.yaml`) that fail the build on a leak
- [ ] CI: build, unit tests, and `verify` on `examples/self-demo` on every push

## v0.3 — more kinds of input

- [ ] `brain learn <file>`: extraction with a local model, for private or bulk material
- [ ] Speaker names in recordings (diarization), and login-gated videos via a safe cookie path
- [ ] Slack channel history and file attachments, not only one thread
- [ ] More source types, all turned into the same timestamped or paged text file:
      PDF and Word documents, images and screenshots (OCR, the engine already exists),
      web pages, Google Docs and Drive, email, Confluence and Jira, Teams and Zoom recordings
      (these need login, so each needs a safe credential path)
- [ ] Verify a quote against the **audio**, not only the transcript (second transcription pass)
- [ ] Incremental add: hash sources, process only what changed
- [ ] Source type `diff:` so a quote can be checked against code changes, not only commit messages

## v0.4 — agents use it directly

- [ ] Read-only MCP server so Claude Code and Codex query the brain without typed commands.
      The reader is fixed at startup and no tool argument can carry permissions (D7); a test asserts it
- [ ] Answer-model option: a stronger hosted model for answers where a small one garbles numbers,
      local stays the default

## v0.5 — people use it

- [ ] One-box web page (Spring Boot returns here, see D4) for people who do not use a terminal
- [ ] Author trust: facts from low-trust authors stay *unconfirmed* until a human confirms them
- [ ] Team sharing: reviewed notes in a shared repo, access control as a pre-filter
- [ ] Demo video generated from notes (a prototype exists privately; it needs a voice engine that
      is not bundled yet)

## Later, only if measured to matter

- Optional graph store behind an interface (D2 records the condition)
- Hosted deployment with real authentication (see [SECURITY.md](SECURITY.md))
- Watch mode: new commits and meeting transcripts appear as drafts automatically

## What could be better today

| Weakness | Effect | Planned in |
|---|---|---|
| Not benchmarked against Claude Code alone | We do not yet know it is better | v0.2 |
| Only the 6 nearest notes reach the model | A relevant note can be missed | v0.2 |
| A 14B local model garbles ambiguous numbers | Check the quotes beside the answer | v0.4 |
| Capture uses Claude Code and your quota | Transcripts of private recordings are sent to it; no local capture yet | v0.3 |
| Speech-to-text can mishear; Slack not tested on a live workspace | Listen at the timestamp for what matters | v0.2 |
| `git:` quotes check the commit message only | Code changes are invisible | v0.3 |
| Needs JDK 27 (preview features) and LM Studio | Heavier setup than a script | open |
| Terminal only | Not for non-developers | v0.5 |
