# Roadmap and status

Where things stand today, then what comes next.

## Where we are today

### What works (verified by running it)

- **`brain capture <anything>`**: one command. Reads a repo, folder, text, PDF, Word, PowerPoint,
  image, subtitles, web page, Google Doc, video link, recording or Slack thread; a **local model**
  proposes notes; code keeps only those whose quote is found word for word in the source and
  attaches the source itself (commit, file, page, slide, minute); you accept. Nothing leaves the
  machine except fetching the thing you asked for. Run for real on a Word file, a PDF, an image, a
  web page, a screen recording, a YouTube video and a 39-commit repository. See [INPUTS.md](INPUTS.md).
- **`brain ask`**: local Qwen3 14B, about 7 s per question. Cites notes, prints sources from the notes
  themselves, declines with `NOT_IN_THE_NOTES`. `--topic` and `--as-of`. Fuses embedding and exact-word
  search, keeps 8 notes, adds the replacement of any outdated note it retrieves.
- **`brain mcp`**: a read-only Model Context Protocol server (`brain_search`, `brain_ask`,
  `brain_topics`). Verified with a real Claude Code run: it found the tool unprompted and quoted the
  notes. No tool takes a path or permission; a test enforces it. `brain init` writes the `.mcp.json`.
- **`brain init`, `verify`, `review`, `eval`, `doctor`**: make a brain, re-check every quote with no AI,
  accept drafts later, run gold questions, check the environment.
- **`demo/`**: a ready brain about this project, 18 notes, all verified against `docs/DECISIONS.md`;
  `eval.yaml` has 5 questions (5 of 5 pass on the automatic check).
- **Tests**: 75 unit tests (readers for every format, the extractor's gate, Slack against a mock
  server, MCP safety, review, init), run by GitHub Actions on every push.

### Measured, and how far to trust it

- **`ask`, private project, 13 gold questions:** 11 right on the first run, 13 right after two
  retrieval and prompt fixes and two note rewrites, so two of those are not blind passes. The notes
  and the answer key were written by the same agent. After adding exact-word search the answers were
  the same: no regression, no measurable gain.
- **Local `capture` quality, on one real repository** (39 commits and 4 docs, 165,000 characters, a
  14B local model): 12 minutes, **103 notes, every quote verified**. 71 other proposals were thrown
  away because their quote was not in the source (the gate working). 2 of 31 parts could not be read
  even after a retry. Against 20 notes written by hand for the same repo: every source I used also
  produced notes (20 of 20), but the **same sentence** was picked for only 6 of 20, and by eye the
  facts I cared most about (a tool added and later removed) were mostly missed. So the notes are
  grounded, not complete. Replacement guesses (`--history`): 15 suggested, about half plausible, so
  they are off by default (D16). One repository, one run: treat these as a first measurement.
- **Trust level: low to medium.** It shows the design works; it does not show it beats Claude Code
  alone. That benchmark ([BENCHMARK.md](BENCHMARK.md)) has **not been run**.

### Known problems

- **A 14B local model misses facts** a person would keep, and sometimes quotes more than needed. The
  gate stops wrong notes, not missing ones.
- **Replacement detection is weak** and therefore opt-in (`--history`, D16): about half the guesses
  are right, and you see each one in the review.
- **Capture is slow on large sources**: about 25 seconds per 6,000 characters on the 14B model (12
  minutes for a 39-commit repository). It runs once. `BRAIN_EXTRACT_MODEL` can point at a smaller, faster model.
- Speech-to-text and OCR mishear names and numbers; the check cannot catch that (only a person
  listening or looking can).
- The local answer model can leave a relevant note out of its citations and swaps ambiguous numbers.
  Through MCP, Claude writes the answer instead.
- Slack was tested against a mock server, not a live workspace.
- Notes were once lost from an uncommitted folder during development and the cause was never found.
  `brain init` makes your brain a git repository; commit it.

### Not built

Excel, email, Confluence, Jira, Notion, login-gated pages and videos, speaker names, Slack history
and files, web page, access control, injection evals, watch mode, deployment. The README must never
claim these.

---

## What comes next

Order matters: **prove it, then widen the inputs, then widen the users.** To propose or request a
change, open an issue, or run `/improve` in Claude Code, which takes the top open item, writes a
failing test first, and records the result.

### Done

- **v0.1** the verified core: notes with quotes, dates and replacement links; `verify`, `ask`, `eval`
- **v0.2** video, recordings, Slack; read-only MCP server; hybrid search; CI
- **v0.3** one command, `capture`, for every input, written by a local model and gated by code;
  your brain in its own folder; four visible folders in the repository

### v0.4: prove it is worth using

The benchmark in [BENCHMARK.md](BENCHMARK.md) has a fixed rule: if Claude Code alone is within one
question of this tool on the history questions, the right product is a Claude Code skill, not a
program. So this comes first.

- [ ] Run the benchmark: Claude Code alone vs a knowledge-graph tool vs second-brain, three runs each
- [ ] Raise the extractor's recall of history facts (added, removed, replaced): measured weak on the
      first real run. Ideas: a prompt pass aimed at commits that add or remove things, smaller parts, a
      stronger local model. Measure on more than one repository, with a hand-checked key
- [ ] Claim-support gate: refuse an answer whose subject appears in no retrieved note
- [ ] Prompt-injection evals that fail the build on a leak

### v0.5: more sources, better quality

- [ ] Excel, email (`.eml`), Confluence, Jira, Notion; Teams and Zoom recordings (login: each needs a
      safe credential path)
- [ ] Speaker names in recordings; Slack channel history and files
- [ ] Verify a quote against the **audio**, not only the transcript
- [ ] Incremental capture: hash sources, process only what changed; capture in the background
- [ ] Source type `diff:` so a quote can be checked against code changes, not only commit messages
- [ ] Optional stronger writer for notes and answers (a hosted model), local stays the default

### v0.6: people use it

- [ ] A small web page for people who do not use a terminal
- [ ] Author trust: facts from low-trust authors stay *unconfirmed* until a human confirms them
- [ ] Team sharing: reviewed notes in a shared repository, access control as a pre-filter

### Later, only if measured to matter

- Optional graph store behind an interface (D2 records the condition)
- Hosted deployment with real authentication (see [SECURITY.md](SECURITY.md))
- Watch mode: new commits and meeting transcripts appear as drafts automatically
