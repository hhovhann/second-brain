---
id: mcp-is-read-only
type: decision
valid_from: 2026-10-01
status: current
repo: .
source: file:docs/DECISIONS.md
quote: so no tool may take `grants`, and no tool may write.
---
# MCP server is read-only, reader fixed at startup
The planned MCP server cannot write and takes no permission arguments, because a tool argument is written by a model, possibly from corpus text. Not built yet.
