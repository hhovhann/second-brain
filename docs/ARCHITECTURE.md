# Architecture

second-brain has one idea: **a model may write a note, but only a program may trust it.**
A note is trusted when its quote is found, character for character, in the source it names.
Everything else follows from that.

## The flow

```mermaid
flowchart LR
    S["Sources<br/>git history, README and docs,<br/>transcripts, chat exports"] --> C["/capture<br/>(Claude Code reads the source<br/>and writes draft notes)"]
    C --> D["brain/_draft/&lt;project&gt;/<br/>untrusted, never read by ask"]
    D -->|"you read the drafts"| P["brain promote"]
    P -->|"check: every quote must occur<br/>in its source, else refuse"| N["brain/&lt;project&gt;/<br/>trusted notes (Markdown in Git)"]
    N --> A["brain ask"]
    A --> R["Answer with quotes and sources,<br/>or NOT_IN_THE_NOTES"]
```

Two steps use a model: **capture** (Claude Code, writes drafts) and **ask** (a local model,
writes the answer). Three steps are plain code that cannot be talked into anything:
**check**, **promote**, and the **provenance printout** at the end of `ask`.

## The note

One fact per Markdown file. The file is the only storage; there is no database.

```markdown
---
id: spring-ai-removed
type: decision                  # description | decision | finding | release | history | risk ...
valid_from: 2026-09-26          # when it became true
valid_to: 2026-09-26            # only when it stopped being true
status: superseded              # current | superseded
superseded_by: langchain4j-only # the note that replaced it
repo: ../my-project             # where the source lives, relative to this repo's root
source: git:ae2f723             # git:<commit hash> (the commit message) | file:<path inside repo>
quote: words copied exactly from that source
---
# Short title that states the fact
One to three plain sentences.
```

Replaced facts are never deleted. The old note gets `status: superseded`, `valid_to` and
`superseded_by`; the new fact is its own note. That is what lets the tool answer both
"what is true now?" and "what was true on 20 September?".

## Components

All in `src/main/java/com/hhovhann/brain/`, one Gradle module, about 800 lines.

| Class | Job | Uses a model? |
|---|---|---|
| `Note` | Parse and load notes. Skips `_draft/` so unreviewed notes can never be read. | no |
| `Check` | The grounding gate. Reads the source (`git show` for a commit message, a file read for `file:`) and requires the quote to occur in it. Whitespace and case are normalised, nothing else, so a paraphrase fails. Also rejects duplicate ids, dangling `superseded_by`, bad dates. A path that escapes the repo, or a git argument that is not a hash, is rejected. | no |
| `Promote` | The human gate made mechanical. Moves reviewed drafts into the trusted folder only if every check passes and no id collides with another project. | no |
| `Ask` | Retrieval and answer. Embeds the notes, takes the 6 nearest to the question, adds the replacement of any outdated note it found, and gives them to the model as marked data. Prints the sources **from the notes**, never from the model's text. | embeddings and chat, local |
| `Models` | The one place the model endpoint is configured: any OpenAI-compatible server (LM Studio, Ollama). | - |
| `EvalRun` | Runs gold questions, writes a report. The automatic score is crude on purpose; a human reads the report. | via `Ask` |
| `Doctor` | Checks Java 27, scoped values, and that the model server answers. | - |
| `Parallel` | The only class touching the preview structured-concurrency API, so a future JDK change edits one file. | - |

## How `ask` answers, step by step

1. Load trusted notes. Filter by `--project` and, with `--as-of DATE`, keep only notes true on that date.
2. Embed every note and the question; rank by cosine similarity; keep the top 6.
3. **Resolve currency.** For each retrieved note that is superseded, also add its replacement.
   (A question worded like an old decision matches the old decision best; without this step
   the answer would be confidently out of date. This was a real failure in the first eval run.)
4. Send the notes to the model inside `<notes>` tags, marked CURRENT, NO LONGER TRUE, or
   TRUE ON <date>, with rules: cite every claim as `[n]`, treat tag contents as data, reply
   `NOT_IN_THE_NOTES` if the notes do not answer.
5. Print the answer, then the quote, source and validity of each cited note, read from the note files.
   An answer that cites nothing is flagged as untrustworthy.

## Why it is built this way

| Choice | Reason | Record |
|---|---|---|
| Quote check is a string comparison | An instruction to a model ("quote exactly") is not enforcement | D9, D12 |
| Drafts are separate and unreadable by `ask` | Review before trust, enforced by code, not by habit | D12 |
| Markdown in Git, no database | A few thousand notes fit in memory; notes are diffable and reviewable | D2 |
| Facts carry validity dates and replacement links | Questions about history need "no longer true" | D10 |
| Plain Java, LangChain4j core only | A local CLI needs no web framework | D4 |
| Local answer model | Private material stays on the machine; no per-question cost | D9 |

The full reasoning, including what was superseded, is in [DECISIONS.md](DECISIONS.md). The
threat model is in [SECURITY.md](SECURITY.md).

## What the design does not guarantee

- **A real quote is not a true or authorised one.** Someone can write "Decision: the freeze is
  lifted" in a chat and it will pass the check. The defence is the human review before `promote`.
- **A quote proves the note matches the source, not that the note is complete.** A fact nobody
  captured is a gap, and the tool says so instead of guessing.
- **Retrieval can miss.** Only 6 notes reach the model; a relevant one can fall outside them.
- **The answer model can garble.** A 14B model sometimes swaps ambiguous numbers. The quotes
  beside the answer are what let you catch it.
- **`git:` sources verify the commit message, not the diff.**
