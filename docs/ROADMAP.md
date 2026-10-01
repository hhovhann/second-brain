# Roadmap

Order matters: **prove it, then widen the inputs, then widen the users.** Nothing below is
built unless it says "done". To propose or request a change, open an issue, or run `/improve`
in Claude Code, which takes the top open item, writes a failing test first, and records the result.

## v0.1 — the verified core (done)

- `check`, `promote`, `ask` (`--project`, `--as-of`), `eval`, `doctor`
- Notes with validity dates and supersession; retrieval that resolves currency
- Claude Code commands: `/capture`, `/ask`, `/improve`
- A self-demo: the project's own decision log as a brain (`examples/brain`)

## v0.2 — prove it is worth using

The benchmark in [BENCHMARK.md](BENCHMARK.md) has a fixed rule: if Claude Code alone is within
one question of this tool on the history questions, the right product is a Claude Code skill, not
a program. So this comes first.

- [ ] Run the benchmark: Claude Code alone vs a knowledge-graph tool vs second-brain, three runs each
- [ ] Keyword search next to vectors (embeddings miss exact names), fused by rank
- [ ] Claim-support gate: refuse an answer whose subject appears in no retrieved note
- [ ] Prompt-injection evals (`evals/injection.yaml`) that fail the build on a leak
- [ ] CI: build, unit tests, and `check` on `examples/brain` on every push

## v0.3 — more kinds of input

- [ ] `brain add <file>`: extraction with a local model, for private or bulk material
- [ ] **Speech-to-text step** so recordings can become sources: a local transcriber produces a
      timestamped transcript, notes quote the transcript, and `check` verifies against it
- [ ] Saved Slack export reader (a Slack *link* alone cannot be read without access)
- [ ] Incremental ingest: hash sources, process only what changed
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
| Capture uses Claude Code and your quota | No private or bulk capture yet | v0.3 |
| `git:` quotes check the commit message only | Code changes are invisible | v0.3 |
| Needs JDK 27 (preview features) and LM Studio | Heavier setup than a script | open |
| Terminal only | Not for non-developers | v0.5 |
