# My brain

Ask *why* things are the way they are, and get the answer with a quote you can verify.

```
notes/     the brain: small notes, one fact each, with the exact quote and where it came from
sources/   text copies of what you added (videos, PDFs, web pages, chats), so quotes stay checkable
```

You never edit these by hand. Two commands:

1. **Capture** anything:  `brain capture <anything> [topic]`
   A repo or folder, a file (pdf, docx, pptx, md, txt, image, subtitles), a web page or Google Doc
   link, a video link or recording, or a Slack message link. It reads it, writes the notes, checks
   that every quote really is in the source, and asks you to accept them.
2. **Ask**:  `brain ask "why did we drop the page graph?"`

Every answer shows the quote and where it came from, or says `NOT_IN_THE_NOTES`.
Inside Claude Code the same two are `/capture` and `/ask`, and Claude can also search the brain by
itself (see `.mcp.json`).

This folder is yours and private. It is a git repository (made by `brain init`); keep it that way
so notes are never lost, and do not publish it if it holds private material.
