---
id: drafts-are-untrusted
type: decision
valid_from: 2026-10-01
status: current
repo: .
source: file:docs/DECISIONS.md
quote: `Note.loadAll` skips `_draft/`; only `promote` moves notes out
---
# Drafts are untrusted by construction
Only promoted notes are ever read when answering. Promotion refuses if any quote, id or supersession link fails to verify.
