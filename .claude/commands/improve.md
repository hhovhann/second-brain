# Improve

Make second-brain better in one small, measured step. (This command is for working on the tool
itself, in this repository.)

Argument: an optional topic. With none, take the first open item under **What comes next** in
`docs/ROADMAP.md`.

1. Read `docs/ROADMAP.md` and `docs/DECISIONS.md`. Do not reverse a current decision without new
   evidence; if there is some, say what it is.
2. State the problem as a failing case first: a unit test, or a question in a test-questions file
   (see `demo/eval.yaml`). Run it and see it fail.
3. Change the smallest thing that fixes the cause (often a note, retrieval or the prompt, not
   more machinery). Keep it simple: no new infrastructure without a measured reason.
4. `./gradlew test`, and `brain eval eval.yaml second-brain` from `demo`. Read the
   report yourself.
5. Record: a new entry in `docs/DECISIONS.md` (supersede, never delete), and update the
   "Where we are today" section of `docs/ROADMAP.md`.
6. Report what changed, the before/after numbers, and what you are unsure about.
   Do not commit or push unless asked.

$ARGUMENTS
