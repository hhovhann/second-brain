# Ask

Answer a question from the trusted notes, using the local model (no Claude tokens), and show
the quotes behind the answer.

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/27.0.0-amzn
build/install/second-brain/bin/second-brain ask [--project <p>] [--as-of YYYY-MM-DD] "$ARGUMENTS"
```

Rebuild first with `./gradlew installDist` if sources changed. Report the answer and the
printed Sources block as they are. If the result is `NOT_IN_THE_NOTES`, say plainly that this
is a **gap**, and offer: add a source with `/capture`, or note it in `docs/STATUS.md` backlog.
If an answer looks wrong, do not argue with it: add the question to `evals/*.yaml` and follow
the self-improvement loop in `CLAUDE.md`.

$ARGUMENTS
