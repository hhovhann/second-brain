# Improve

Make second-brain better in one small, measured step.

Argument: an optional topic. With none, take the top item of the **Backlog** in `docs/STATUS.md`.

1. Read `docs/STATUS.md` and `docs/DECISIONS.md`. Do not reverse a current decision without new
   evidence; if there is some, say what it is.
2. State the problem as a failing case first: a question in `evals/*.yaml`, or a unit test.
   Run it and see it fail.
3. Change the smallest thing that fixes the cause (often a note, retrieval or the prompt, not
   more machinery). Keep it simple: no new infrastructure without a measured reason.
4. `./gradlew test` and `brain eval evals/<file>.yaml <project>`. Read the report yourself.
5. Record: a new entry in `docs/DECISIONS.md` (supersede, never delete), update
   `docs/STATUS.md` (what works, what changed, what is still open).
6. Report what changed, the before/after numbers, and what you are unsure about.
   Do not commit or push unless asked.

$ARGUMENTS
