# My brain

Ask *why* things are the way they are, and get the answer with a quote you can verify.

```
notes/     the brain: small notes, one fact each, with the exact quote and where it came from
sources/   text copies of videos, recordings and chats you added
```

You never edit these by hand. Three steps:

1. **Add** something. A repo or a text file needs no add. For a video, recording or Slack thread:
   `brain add <video-url | recording-file | slack-link>`
2. **Learn** it. Open Claude Code in this folder and run `/learn <what> <topic>`
   (for example `/learn ../my-project my-project`). It writes draft notes.
   Then read them and accept: `brain review my-project`
3. **Ask**: `brain ask "why did we drop the page graph?"`

Every answer shows the quote and where it came from, or says `NOT_IN_THE_NOTES`.
`brain verify` re-checks that every quote is still in its source.

This folder is yours and private. It is a git repository (made by `brain init`); keep it that way
so notes are never lost, and do not publish it if it holds private material.
