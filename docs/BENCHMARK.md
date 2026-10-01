# Benchmark: is a second brain worth building?

Question: on a real project's history, does second-brain answer better than what already
exists? If not, we ship a Claude Code skill and stop. Decided **before** any result (D8).

## Subject

A real project of the maintainer's, kept private: 36 commits over five months, README, CHANGELOG,
a long learning path, and reasons that live only in commit bodies. It already contains reversals
(a framework added and removed; tools removed and reinstated; a graph added and removed) — real
temporal structure, not planted. Keys: `a questions file`, 13 questions, not published.
The format is shown in `demo/eval.yaml`.

## Arms

| Arm | What answers | Notes |
|---|---|---|
| **A** | Claude Code alone in the repo (`claude -p`), no extra tools | the thing second-brain must beat |
| **B** | Graphify graph of the repo, queried | the knowledge-graph alternative |
| **C** | second-brain (M1) | facts + verified quotes + supersession |

LangGraph4j is not an arm: it orchestrates, it does not remember.

## Measured per question

Right (every `expect`, no `forbid`) · grounded (each cited commit or file line exists) ·
declined correctly (C13) · tokens in/out · wall time. Verdicts stay separate — *missed* and
*wrong* are not averaged — because the failures are not interchangeable.

## Fairness rules (each learned the hard way in an earlier project)

1. Same model for the arms that call one. Count every call's tokens in a wrapper, even failed ones.
2. Alternate which arm goes first; warm the model up.
3. No arm gets a handicapped tool. Review the measurement itself before trusting a number.
4. One run is not a result: three runs per arm, report the spread.
5. Keys are checked against git, not against an arm's own answer.

## Decision rule (fixed in advance)

Build on only if arm C, on the `why`, `history`, `reversal`, `as_of` and `decline` questions
(C2–C9, C13 in the private key), is **right on at least 2 more questions than arm A** and **grounded on all it
answers**, at no more than A's tokens. If A is within one question of C there, the tool is a
wrapper: ship the skill. Current-state questions (C1, C12) are a control; C must not be worse.

## Expected, stated now so it can be wrong

A should do well on current-state and on anything in the docs, and miss the C3/C5 commit-body
reasons unless it reads `git log` with bodies; it will rarely cite verbatim. B should find
structure but has no notion of "no longer true". C should win on C3, C4, C8, C9, C13.
