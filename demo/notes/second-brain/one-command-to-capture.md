---
id: one-command-to-capture
type: decision
valid_from: 2026-10-01
status: current
repo: ..
source: file:docs/DECISIONS.md
quote: A local model writes the proposals.
---
# One command captures anything; the local model proposes and code decides
brain capture reads the input, a local model proposes notes, and code keeps only the ones whose quote is found in the source. It never writes the source itself. It replaced the separate add, learn and review steps.
