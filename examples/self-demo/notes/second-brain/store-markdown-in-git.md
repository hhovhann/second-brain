---
id: store-markdown-in-git
type: decision
valid_from: 2026-10-01
status: current
repo: ../..
source: file:docs/DECISIONS.md
quote: Markdown is the source of truth
---
# Markdown in Git is the store, no database
Notes are plain Markdown files kept in Git. There is no database; an in-memory index is built from the files on each run.
