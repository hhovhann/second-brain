package com.hhovhann.brain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Turns a Slack link, a video URL, or a recording on disk into one text file, {@code sources/<id>.md}.
 *
 * <p>Everything downstream already works on text, so this is the only new trust boundary: after
 * it, a note is verified against the transcript with the same string comparison as any file.
 * The check proves the note matches the <em>transcript</em>; whether the transcript matches what
 * was said is the transcriber's accuracy, which is why timestamps are kept for a human to listen.
 *
 * <p>Outside programs (yt-dlp, ffmpeg, tesseract, a Python transcriber) are started with an
 * argument list, never a shell, and a URL goes after {@code --} so it cannot be read as an option.
 */
final class Ingest {

    private static final Set<String> MEDIA = Set.of(
            "mp4", "mov", "mkv", "webm", "m4v", "avi", "mp3", "m4a", "wav", "aac", "flac", "ogg", "opus");
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final int OCR_EVERY_SECONDS = 10;
    private static final int MAX_FRAMES = 360;
    private static final ObjectMapper JSON = new ObjectMapper();

    enum Kind { SLACK, URL, FILE }

    private Ingest() {}

    static Kind classify(String input) {
        if (Slack.isLink(input)) {
            return Kind.SLACK;
        }
        if (input.startsWith("https://") || input.startsWith("http://")) {
            return Kind.URL;
        }
        return Kind.FILE;
    }

    static String idFor(Kind kind, String input) {
        return switch (kind) {
            case SLACK -> {
                Slack.Ref ref = Slack.parse(input);
                yield "slack-" + ref.channel().toLowerCase(Locale.ROOT) + "-" + ref.threadTs().replace(".", "");
            }
            case URL -> "video-" + HexFormat.of().formatHex(sha256(input)).substring(0, 10);
            case FILE -> {
                String file = Path.of(input).getFileName().toString();
                int dot = file.lastIndexOf('.');
                String stem = (dot > 0 ? file.substring(0, dot) : file).toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
                yield "rec-" + (stem.length() > 48 ? stem.substring(0, 48) : stem);
            }
        };
    }

    static int run(Path root, List<String> args) throws IOException, InterruptedException {
        String input = null;
        String name = null;
        boolean screen = false;
        for (int i = 0; i < args.size(); i++) {
            switch (args.get(i)) {
                case "--name" -> name = i + 1 < args.size() ? args.get(++i) : null;
                case "--screen" -> screen = true;
                default -> input = input == null ? args.get(i) : input;
            }
        }
        if (input == null) {
            System.err.println("usage: brain capture <anything>   (video-url, recording, slack-link, repo, pdf, docx, web page, ...)");
            return 2;
        }
        Path out;
        try {
            out = fetch(root, input, name, screen);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("add failed: " + e.getMessage());
            return 1;
        }
        long lines = Files.readAllLines(out).stream().filter(l -> l.startsWith("[")).count();
        System.out.printf("Saved %d lines of text to sources/%s%n", lines, out.getFileName());
        return 0;
    }

    /** Is this a Slack link, a video or recording we can transcribe? Anything else is not Ingest's job. */
    static boolean handles(String input) {
        Kind kind = classify(input);
        return kind == Kind.SLACK || (kind == Kind.URL && Reader.looksLikeVideo(input))
                || (kind == Kind.FILE && Files.isRegularFile(Path.of(input)) && MEDIA.contains(extension(input)));
    }

    /** Turns a Slack link, a video URL or a recording file into {@code sources/<id>.md} and returns that file. */
    static Path fetch(Path root, String input, String name, boolean screen) throws IOException, InterruptedException {
        Kind kind = classify(input);
        if (kind == Kind.FILE && !Files.isRegularFile(Path.of(input))) {
            throw new IOException("not a Slack link, a http(s) URL, or a file: " + input);
        }
        if (kind == Kind.FILE && !MEDIA.contains(extension(input))) {
            throw new IOException("not a recording (expected one of " + new java.util.TreeSet<>(MEDIA) + ")");
        }
        String id = name != null ? name : idFor(kind, input);
        if (!NAME.matcher(id).matches()) {
            throw new IOException("name must be lowercase letters, digits and dashes: " + id);
        }
        Path sources = Files.createDirectories(root.resolve("sources"));
        Path out = sources.resolve(id + ".md");
        Path dir = Files.createTempDirectory("brain-add-");   // audio, video and frames live here, then are deleted
        try {
            String text = switch (kind) {
                case SLACK -> slack(input);
                case URL -> media(input, dir, screen);
                case FILE -> recording(Path.of(input).toAbsolutePath(), input, dir, screen);
            };
            Files.writeString(out, text);
        } finally {
            deleteTree(dir);
        }
        return out;
    }

    private static String slack(String link) throws IOException, InterruptedException {
        String token = Models.env("SLACK_TOKEN", "");
        if (token.isBlank()) {
            throw new IOException("set SLACK_TOKEN to a Slack token that can read the channel "
                    + "(scopes channels:history, groups:history, users:read). See docs/INGEST.md.");
        }
        HttpClient http = HttpClient.newHttpClient();
        Slack.Ref ref = Slack.parse(link);
        List<Slack.Message> messages = Slack.fetchThread(http, Slack.API, token, ref);
        List<String> lines = Slack.lines(messages, Slack.names(http, Slack.API, token, messages));
        return Transcript.render("Slack thread in " + ref.channel(), link, "slack", LocalDate.now(), lines);
    }

    private static String media(String url, Path dir, boolean screen) throws IOException, InterruptedException {
        List<String> info = lines(exec(List.of("yt-dlp", "--no-playlist", "--print", "%(title)s", "--print", "%(duration)s", "--", url), 120));
        String title = info.isEmpty() ? url : info.get(0);
        double minutes = info.size() > 1 && info.get(1).matches("[0-9.]+") ? Double.parseDouble(info.get(1)) / 60 : 0;
        int limit = Integer.parseInt(Models.env("BRAIN_MAX_MINUTES", "240"));
        if (minutes > limit) {
            throw new IOException("video is %.0f minutes; the limit is %d (set BRAIN_MAX_MINUTES)".formatted(minutes, limit));
        }
        System.out.printf("Downloading \"%s\" (%.0f min)...%n", title, minutes);
        Path file;
        if (screen) {
            exec(List.of("yt-dlp", "--no-playlist", "--no-progress", "-f", "bv*[height<=720]+ba/b[height<=720]/b",
                    "--merge-output-format", "mp4", "-o", dir.resolve("video.%(ext)s").toString(), "--", url), 60 * 60);
            file = dir.resolve("video.mp4");
        } else {
            exec(List.of("yt-dlp", "--no-playlist", "--no-progress", "-x", "--audio-format", "wav",
                    "-o", dir.resolve("audio.%(ext)s").toString(), "--", url), 60 * 60);
            file = dir.resolve("audio.wav");
        }
        return transcribe(file, title, url, screen ? "screen-recording" : "video", dir, screen);
    }

    private static String recording(Path file, String shown, Path dir, boolean screen) throws IOException, InterruptedException {
        return transcribe(file, file.getFileName().toString(), shown, screen ? "screen-recording" : "recording", dir, screen);
    }

    private static String transcribe(Path media, String title, String source, String kind, Path dir, boolean screen)
            throws IOException, InterruptedException {
        Path wav = dir.resolve("audio.wav");
        if (!wav.equals(media)) {
            exec(List.of("ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", media.toString(),
                    "-vn", "-ar", "16000", "-ac", "1", wav.toString()), 30 * 60);
        }
        System.out.println("Transcribing locally...");
        String json = exec(List.of(Models.env("BRAIN_PYTHON", "python3"), helper(dir).toString(), wav.toString()), 3 * 60 * 60);
        List<Transcript.Segment> segments = new ArrayList<>();
        for (JsonNode s : JSON.readTree(json)) {
            segments.add(new Transcript.Segment(s.path("start").asDouble(), s.path("text").asText(), false));
        }
        if (screen) {
            System.out.println("Reading on-screen text...");
            segments.addAll(Transcript.screen(ocr(media, dir), OCR_EVERY_SECONDS));
        }
        if (segments.isEmpty()) {
            throw new IOException("nothing was transcribed: the recording may have no speech"
                    + (screen ? " and no readable on-screen text" : " (try --screen to read what is shown)"));
        }
        return Transcript.render(title, source, kind, LocalDate.now(), Transcript.lines(segments));
    }

    /** One OCR string per sampled frame; the frame at index i was taken at i * OCR_EVERY_SECONDS. */
    private static List<String> ocr(Path video, Path dir) throws IOException, InterruptedException {
        Path frames = Files.createDirectories(dir.resolve("frames"));
        exec(List.of("ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", video.toString(),
                "-vf", "fps=1/" + OCR_EVERY_SECONDS, "-frames:v", String.valueOf(MAX_FRAMES),
                frames.resolve("f%05d.png").toString()), 30 * 60);
        List<Path> files;
        try (Stream<Path> s = Files.list(frames)) {
            files = s.filter(p -> p.toString().endsWith(".png")).sorted().toList();
        }
        List<String> texts = new ArrayList<>();
        for (Path f : files) {
            texts.add(exec(List.of("tesseract", f.toString(), "stdout", "--psm", "6"), 120));
        }
        return texts;
    }

    static String exec(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        Path err = Files.createTempFile("brain-ingest", ".err");
        try {
            Process process;
            try {
                process = new ProcessBuilder(command).redirectError(err.toFile()).start();
            } catch (IOException e) {
                throw new IOException(command.get(0) + " is not installed or not on PATH (" + e.getMessage() + ")");
            }
            process.getOutputStream().close();
            // Read stdout on its own thread so the timeout below can fire even if the tool hangs.
            var stdout = new java.util.concurrent.atomic.AtomicReference<byte[]>(new byte[0]);
            Thread reader = Thread.ofVirtual().start(() -> {
                try {
                    stdout.set(process.getInputStream().readAllBytes());
                } catch (IOException ignored) {
                    // the process was killed; whatever was read is enough for the error message
                }
            });
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(command.get(0) + " timed out after " + timeoutSeconds + "s");
            }
            reader.join();
            if (process.exitValue() != 0) {
                String tail = Files.readString(err).lines().skip(Math.max(0, Files.readString(err).lines().count() - 5))
                        .reduce("", (a, b) -> a + "\n  " + b);
                throw new IOException(command.get(0) + " failed (exit " + process.exitValue() + "):" + tail);
            }
            return new String(stdout.get(), StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(err);
        }
    }

    /** The speech-to-text helper ships inside the program; write it out next to the audio to run it. */
    private static Path helper(Path work) throws IOException {
        Path script = work.resolve("transcribe.py");
        try (java.io.InputStream in = Ingest.class.getResourceAsStream("/transcribe.py")) {
            if (in == null) {
                throw new IOException("missing built-in transcribe.py");
            }
            Files.copy(in, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return script;
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> all = Files.walk(dir)) {
            all.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // a temp folder that cannot be removed is not worth failing the command
        }
    }

    private static List<String> lines(String text) {
        return text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    private static String extension(String file) {
        int dot = file.lastIndexOf('.');
        return dot < 0 ? "" : file.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
