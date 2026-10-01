# Ask

Answer a question from this brain and show the quotes behind the answer.

```bash
brain ask [--topic <name>] [--as-of YYYY-MM-DD] "$ARGUMENTS"
```

Report the answer and the printed Sources exactly as they are. If the result is
`NOT_IN_THE_NOTES`, say plainly that the reason or fact was never recorded, and offer to add a
source with `/learn`. If an answer looks wrong, say so and point at the quote beside it; do not
argue with it.

$ARGUMENTS
