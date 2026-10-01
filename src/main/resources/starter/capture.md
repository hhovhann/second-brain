# Capture

Add something to this brain. One command does everything: it reads the input, writes the notes,
checks that every quote really is in the source, and asks the person to accept them.

Argument: `<anything> [topic]`. `anything` can be a repo or folder, a file (pdf, docx, pptx, md, txt,
image, subtitles), a web page or Google Doc link, a video link or screen recording, or a Slack message
link. `topic` is a short lowercase name with dashes; if it is missing the command picks one.

```bash
brain capture $ARGUMENTS
```

- Run it as it is. It uses the local model, so it needs LM Studio running; if it says the model did
  not answer, tell the person to start it (`brain doctor` checks).
- It asks the person to accept the notes. Your shell has no keyboard, so it will print the new notes
  and stop. Show them to the person, and only after they say yes run `brain review <topic> --yes`.
- A link that needs a login cannot be read. Ask the person to save it as PDF or text and capture the file.
- Report how many notes were drafted, how many proposals were dropped because the quote was not in
  the source, and the most important facts. Do not add notes by hand and do not weaken the check.
- If this is a private source, say that nothing left the machine: the reading and the notes were done
  locally.

$ARGUMENTS
