---
id: why-no-database
type: decision
valid_from: 2026-10-01
status: current
repo: ..
source: file:docs/DECISIONS.md
quote: a local tool should not need Docker.
---
# Why there is no database
Postgres and Redis went unused in the earlier prototype, and a local tool should not need Docker. A few thousand notes fit in memory, so an index is built from the files on each run.
