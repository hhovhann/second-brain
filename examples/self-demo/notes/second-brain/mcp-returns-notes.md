---
id: mcp-returns-notes
type: decision
valid_from: 2026-10-01
status: current
repo: ../..
source: file:docs/DECISIONS.md
quote: the calling model writes the answer
---
# The MCP server returns notes; the calling model writes the answer
Claude Code and other agents call brain_search and get notes with verified quotes, then write the answer themselves. This was built and tested on 2026-10-01 and replaces the earlier plan note that the MCP server was not built yet.
