# Architecture

second-brain has one idea: **a model may write a note, but only a program may trust it.**
A note is trusted when its quote is found, word for word (ignoring spacing and capital letters), in the source it names.
Everything else follows from that.

## The flow

![How second-brain works](architecture.svg)

Two steps use a model: **capture** (Claude Code writes draft notes) and **ask** (a local model
writes the answer). Three steps are plain code that cannot be talked into anything: **check**,
**review**, and the **provenance printout** at the end of `ask`. The left half of the picture is
where outside text lives (a model may read it, nothing there is trusted); the right half is where
only verified notes exist.

### What happens when you ask

```mermaid
sequenceDiagram
    actor You
    participant B as brain ask
    participant N as trusted notes
    participant E as embedding model (local)
    participant L as answer model (local)
    You->>B: "Why did we drop X?"  [--project] [--as-of date]
    B->>N: load notes, filter by project and date
    B->>E: embed notes and question
    E-->>B: vectors
    Note over B: fuse meaning and exact-word rankings, keep 8,<br/>add the replacement of any outdated one
    B->>L: notes as marked data + rules<br/>(cite [n], or reply NOT_IN_THE_NOTES)
    L-->>B: answer with [n] citations
    B-->>You: answer + quote, source and validity<br/>read from the note files, not from the model
```

### What happens when you add a source

```mermaid
flowchart LR
    A["Slack link"] --> I
    B["Video URL"] --> I
    C["Recording file"] --> I
    I["brain add<br/>yt-dlp · ffmpeg · Whisper · OCR · Slack API"] --> T["sources/&lt;id&gt;/<id>.md<br/>[00:12:03] one segment per line"]
    G["Git repo, text files"] --> K
    T --> K["/learn<br/>Claude Code drafts notes"]
    K --> D["notes/_draft/"]
    D -->|"you review"| P["brain review<br/>(check: every quote in its source)"]
    P --> N["brain/&lt;project&gt;/"]
    style D fill:#fff4de,stroke:#c98a14
    style N fill:#e5f5ec,stroke:#2f9e5f
    style P fill:#e5f5ec,stroke:#2f9e5f
```

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

All in `src/main/java/com/hhovhann/brain/`, one Gradle module, about 1,300 lines.

| Class | Job | Uses a model? |
|---|---|---|
| `Note` | Parse and load notes. Skips `_draft/` so unreviewed notes can never be read. | no |
| `Init`, `Ingest`, `Slack`, `Transcript` | Turn a Slack link, video URL or recording into `sources/<id>.md`: `yt-dlp` and `ffmpeg` fetch and convert, `bin/transcribe.py` runs a local Whisper, `tesseract` reads on-screen text, Slack's Web API reads a thread with the user's token (sent only to slack.com). Outside programs get argument lists, never a shell; a URL goes after `--`. Detail: [INGEST.md](INGEST.md). | speech-to-text, local |
| `Check` | The grounding gate. Reads the source (`git show` for a commit message, a file read for `file:`, where a `#t=00:12:03` suffix is only a pointer) and requires the quote to occur in it. Whitespace and case are normalised, nothing else, so a paraphrase fails. Also rejects duplicate ids, dangling `superseded_by`, bad dates. A path that escapes the repo, or a git argument that is not a hash, is rejected. | no |
| `Review`, `Promote` | The human gate made mechanical. Moves reviewed drafts into the trusted folder only if every check passes and no id collides with another project. | no |
| `Ask`, `Keyword` | Retrieval and answer. Ranks notes by meaning (embeddings) and by exact words (BM25), fuses the two, keeps 8, adds the replacement of any outdated note it found, and gives them to the model as marked data. Prints the sources **from the notes**, never from the model's text. | embeddings and chat, local |
| `Mcp` | A read-only MCP server over stdio: `brain_search`, `brain_ask`, `brain_topics`. The brain is fixed at startup and no argument names a path or permission (D7, D14). Claude Code writes the answer from the notes it gets back. | optional |
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
  lifted" in a chat and it will pass the check. The defence is the human review in `brain review`.
- **A quote proves the note matches the source, not that the note is complete.** A fact nobody
  captured is a gap, and the tool says so instead of guessing.
- **The answer model can omit a relevant note.** Search ranks it, but the local model chooses what to cite. Through MCP, Claude writes the answer instead.
- **The answer model can garble.** A 14B model sometimes swaps ambiguous numbers. The quotes
  beside the answer are what let you catch it.
- **`git:` sources verify the commit message, not the diff.**
