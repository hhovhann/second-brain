# Status

Updated 2026-10-01. Read this first in a new session.

## What works (verified by running it)

- **Notes**: Markdown files with a verbatim quote, a source, validity dates and replacement links.
- **`check` / `promote`**: drafts are untrusted until promoted; promote refuses if any quote,
  id or supersession link fails. Path traversal and git-argument injection are rejected (tested).
- **`ask`**: local Qwen3 14B, about 7 s per question, no hosted-model tokens. Cites notes,
  prints sources from the notes themselves, declines with `NOT_IN_THE_NOTES`. Supports
  `--project` and `--as-of`. Adds the replacement of any outdated note it retrieves.
- **`eval`**: runs a gold-question file and writes a report.
- **`/capture`, `/ask`, `/improve`** Claude Code commands in `.claude/commands/`.
- **Self-demo**: `examples/brain` holds 14 notes about this project, all verified against
  `docs/DECISIONS.md`; `examples/self-eval.yaml` has 5 questions (5 of 5 pass on the automatic check).
- **`ingest`**: video URL (yt-dlp, local Whisper), recording file, `--screen` (OCR of on-screen
  text), Slack thread link (own token). Video URL and screen recording were run for real; the
  Slack path is covered by unit tests against a mock server only. See `docs/INGEST.md`.
- 32 unit tests.

## Measured, and how far to trust it

On a private project (13 gold questions): 11 right on the first run, 13 right after two
retrieval/prompt fixes and two note rewrites, so two of those are not blind passes. The notes and
the answer key were written by the same agent. **Trust level: low to medium.** It shows the
design works; it does not show it beats Claude Code alone. That benchmark
([BENCHMARK.md](BENCHMARK.md)) has **not been run**.

## Known problems

- A small answer model swaps ambiguous numbers; notes must state one measurement per line.
- Only the 6 nearest notes reach the model, so a relevant note can be missed (seen in the
  self-demo: the "why no database" note exists but the answer to "why drop the graph database?"
  did not cite it).
- Speech-to-text mishears names and numbers; the quote check cannot catch that (only a person
  listening at the timestamp can).
- `/capture` sends transcripts to Claude Code, so private recordings are not private end to end.
- Notes were once lost from an uncommitted folder during development and the cause was never
  found. Commit your notes (or keep them in a private repository) as soon as you have them.

## Not built

Model-based extraction (`brain add`), speaker names, login-gated videos, Slack history and files,
MCP server, web UI, keyword search, access control, injection evals, watch mode, deployment. The README must
never claim these. Order and plan: [ROADMAP.md](ROADMAP.md).
