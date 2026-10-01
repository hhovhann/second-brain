package com.hhovhann.brain;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Turns whatever a person points at into text a quote can be checked against.
 *
 * <p>Anything that is not already plain text (a PDF, a Word file, a web page, an image, a video,
 * a Slack thread) is converted once and saved as {@code sources/<id>.md}, so the brain stays
 * verifiable even if the original moves or changes. A git repository is read in place: its
 * commit messages and docs are the sources, so a note can point at the real commit.
 */
final class Reader {

    /** One piece of source text and where a quote found in it can be verified. */
    record Unit(String text, String repo, String ref, LocalDate date) {}

    record Gathered(String label, String topic, List<Unit> units) {}

    private static final Set<String> TEXT = Set.of("md", "markdown", "txt", "text", "rst", "log", "csv", "tsv", "json", "yaml", "yml", "xml");
    private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff", "webp");
    private static final Set<String> VIDEO_HOSTS = Set.of("youtube.com", "youtu.be", "vimeo.com", "loom.com", "twitch.tv", "dailymotion.com", "wistia.com", "ted.com", "tiktok.com", "instagram.com", "facebook.com", "x.com", "twitter.com");
    private static final Set<String> SKIP_DIRS = Set.of("node_modules", "build", "target", "dist", "out", ".git", ".gradle", ".idea", "venv", ".venv");
    private static final Pattern GOOGLE = Pattern.compile("https://docs\\.google\\.com/(document|presentation|spreadsheets)/d/([A-Za-z0-9_-]{10,})(?:[/?#].*)?");
    private static final Pattern CUE_TIME = Pattern.compile("^\\s*(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})[.,]\\d{1,3}\\s*-->.*");
    private static final long MAX_BYTES = 8_000_000L;
    private static final int MAX_DOC_FILES = 40;
    private static final int MAX_COMMITS = 400;
    private static final int MIN_COMMIT_CHARS = 25;

    private Reader() {}

    // ---------------------------------------------------------------- entry point

    static Gathered gather(Path home, String input, boolean screen) throws IOException, InterruptedException {
        if (Slack.isLink(input) || Ingest.handles(input)) {
            return fromSource(home, Ingest.fetch(home, input, null, screen), null);
        }
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return fromUrl(home, input, screen);
        }
        Path path = Path.of(input).toAbsolutePath().normalize();
        if (Files.isDirectory(path)) {
            return Files.isDirectory(path.resolve(".git")) ? repo(home, path) : folder(home, path);
        }
        if (Files.isRegularFile(path)) {
            return fromFile(home, path);
        }
        throw new IOException("not found: " + input + " (give a repo or folder, a file, a web page or video link, or a Slack link)");
    }

    static boolean looksLikeVideo(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^(www|m)\\.", "");
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            return VIDEO_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h))
                    || path.matches(".*\\.(mp4|mov|mkv|webm|m4v|mp3|m4a|wav|ogg)$");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- web

    private static Gathered fromUrl(Path home, String url, boolean screen) throws IOException, InterruptedException {
        if (looksLikeVideo(url)) {
            return fromSource(home, Ingest.fetch(home, url, null, screen), null);
        }
        Matcher google = GOOGLE.matcher(url);
        String fetchUrl = url;
        String title = null;
        if (google.matches()) {
            String kind = google.group(1);
            fetchUrl = "https://docs.google.com/" + kind + "/d/" + google.group(2) + "/export?format=" + (kind.equals("spreadsheets") ? "csv" : "txt");
            title = "Google " + kind.replace("spreadsheets", "sheet");
        }
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(fetchUrl))
                .header("User-Agent", "second-brain/" + Mcp.VERSION)
                .timeout(Duration.ofSeconds(60))
                .GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        String host = response.uri().getHost() == null ? "" : response.uri().getHost();
        if (google.matches() && (response.statusCode() >= 400 || host.contains("accounts.google"))) {
            throw new IOException("Google would not give out this document. Share it as 'anyone with the link can view', "
                    + "or download it as PDF or Word and capture that file.");
        }
        if (response.statusCode() >= 400) {
            throw new IOException("the page answered " + response.statusCode() + ". If it needs a login, save it as PDF or HTML "
                    + "from your browser and capture that file instead.");
        }
        String type = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
        if (type.startsWith("video/") || type.startsWith("audio/")) {
            return fromSource(home, Ingest.fetch(home, url, null, screen), null);
        }
        byte[] body;
        try (InputStream in = response.body()) {
            body = in.readNBytes((int) MAX_BYTES + 1);
        }
        if (body.length > MAX_BYTES) {
            throw new IOException("that page is larger than " + MAX_BYTES / 1_000_000 + " MB");
        }
        List<String> lines;
        if (type.contains("html")) {
            Document doc = Jsoup.parse(new String(body, StandardCharsets.UTF_8), fetchUrl);
            title = doc.title().isBlank() ? host : doc.title();
            lines = htmlLines(doc);
        } else if (type.contains("pdf")) {
            try (PDDocument pdf = Loader.loadPDF(body)) {
                lines = pdfLines(pdf);
            }
            title = host + " PDF";
        } else if (type.startsWith("text/") || google.matches()) {
            lines = paragraphs(new String(body, StandardCharsets.UTF_8));
            title = title == null ? host : title;
        } else {
            throw new IOException("cannot read content of type '" + type + "' from that address");
        }
        String id = "web-" + slug(host, 24) + "-" + HexFormat.of().formatHex(sha256(url)).substring(0, 6);
        return save(home, id, title, url, "web page", lines);
    }

    // ---------------------------------------------------------------- files

    private static Gathered fromFile(Path home, Path file) throws IOException, InterruptedException {
        String name = file.getFileName().toString();
        String ext = extension(name);
        String id = "doc-" + slug(stem(name), 40) + (ext.isEmpty() ? "" : "-" + ext);
        List<String> lines;
        String kind = "document";
        switch (ext) {
            case "pdf" -> {
                try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
                    lines = pdfLines(pdf);
                }
            }
            case "docx" -> lines = docxLines(file);
            case "pptx" -> lines = pptxLines(file);
            case "html", "htm" -> lines = htmlLines(Jsoup.parse(Files.readString(file), file.toUri().toString()));
            case "vtt", "srt" -> {
                lines = subtitleLines(Files.readString(file));
                kind = "subtitles";
            }
            default -> {
                if (IMAGES.contains(ext)) {
                    lines = ocrLines(file);
                    kind = "image";
                } else if (TEXT.contains(ext) || ext.isEmpty()) {
                    if (Files.size(file) > MAX_BYTES) {
                        throw new IOException(name + " is larger than " + MAX_BYTES / 1_000_000 + " MB");
                    }
                    lines = Files.readString(file).lines().map(String::strip).filter(l -> !l.isEmpty()).toList();   // keep the author's lines
                } else {
                    throw new IOException("cannot read ." + ext + " files yet. Supported: pdf, docx, pptx, html, images, subtitles (vtt, srt), "
                            + "text and markdown, video and audio files, git repos and folders. Export it to PDF or text first.");
                }
            }
        }
        return save(home, id, name, file.toString(), kind, lines);
    }

    private static Gathered repo(Path home, Path repo) throws IOException, InterruptedException {
        String rel = relative(home, repo);
        List<Unit> units = new ArrayList<>();
        List<Path> docs = new ArrayList<>();
        try (Stream<Path> all = Files.walk(repo, 4)) {
            all.filter(Files::isRegularFile)
                    .filter(p -> repo.relativize(p).getNameCount() <= 1 || repo.relativize(p).startsWith("docs"))
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".md") && (repo.relativize(p).startsWith("docs") || n.startsWith("readme") || n.startsWith("changelog")
                                || n.startsWith("contributing") || n.startsWith("architecture"));
                    })
                    .sorted().limit(MAX_DOC_FILES).forEach(docs::add);
        }
        for (Path doc : docs) {
            if (Files.size(doc) <= 300_000) {
                units.add(new Unit(Files.readString(doc), rel, "file:" + repo.relativize(doc), modified(doc)));
            }
        }
        String log = Ingest.exec(List.of("git", "-C", repo.toString(), "log", "--reverse", "--no-merges",
                "--format=%x1e%h%x1f%ad%x1f%B", "--date=short"), 120);
        List<Unit> commits = new ArrayList<>();
        for (String entry : log.split("\u001e")) {
            String[] parts = entry.split("\u001f", 3);
            if (parts.length == 3 && parts[2].strip().length() >= MIN_COMMIT_CHARS) {
                commits.add(new Unit(parts[2].strip(), rel, "git:" + parts[0].strip(), LocalDate.parse(parts[1].strip())));
            }
        }
        units.addAll(commits.size() > MAX_COMMITS ? commits.subList(commits.size() - MAX_COMMITS, commits.size()) : commits);
        if (units.isEmpty()) {
            throw new IOException("nothing to read in " + repo + ": no README or docs, and no commits with a message");
        }
        return new Gathered(repo.getFileName() + " (" + docs.size() + " docs, " + commits.size() + " commits)",
                slug(repo.getFileName().toString(), 40), units);
    }

    private static Gathered folder(Path home, Path dir) throws IOException, InterruptedException {
        String rel = relative(home, dir);
        List<Unit> units = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> all = Files.walk(dir, 3)) {
            files = all.filter(Files::isRegularFile)
                    .filter(p -> Paths.noneMatch(dir.relativize(p), SKIP_DIRS))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted().limit(MAX_DOC_FILES).toList();
        }
        for (Path f : files) {
            String ext = extension(f.getFileName().toString());
            if (TEXT.contains(ext) && Files.size(f) <= 300_000) {
                units.add(new Unit(Files.readString(f), rel, "file:" + dir.relativize(f), modified(f)));
            } else if (Set.of("pdf", "docx", "pptx", "html", "htm", "vtt", "srt").contains(ext)) {
                try {
                    units.addAll(fromFile(home, f).units());
                } catch (IOException e) {
                    System.err.println("  skipped " + dir.relativize(f) + ": " + e.getMessage());
                }
            }
        }
        if (units.isEmpty()) {
            throw new IOException("nothing readable in " + dir + " (looked for text, markdown, pdf, docx, pptx, html, subtitles)");
        }
        return new Gathered(dir.getFileName() + " (" + units.size() + " files)", slug(dir.getFileName().toString(), 40), units);
    }

    /** A source that was converted and saved: the brain keeps its own copy of the text. */
    private static Gathered save(Path home, String id, String title, String origin, String kind, List<String> lines) throws IOException {
        String joined = String.join("", lines).strip();
        if (joined.length() < 40) {
            throw new IOException("found almost no readable text in " + origin + (kind.equals("image") ? " (is the image sharp enough to read?)" : ""));
        }
        Path sources = Files.createDirectories(home.resolve("sources"));
        Path out = sources.resolve(id + ".md");
        if (Files.exists(out) && !Files.readString(out).contains("\nSource: " + origin + "\n")) {
            // a different input with the same name: never overwrite another source's text
            out = sources.resolve(id + "-" + HexFormat.of().formatHex(sha256(origin)).substring(0, 6) + ".md");
        }
        Files.writeString(out, Transcript.render(title, origin, kind, LocalDate.now(), lines));
        return fromSource(home, out, title);
    }

    static Gathered fromSource(Path home, Path file, String title) throws IOException {
        String text = Files.readString(file);
        int body = text.startsWith("# Transcript:") ? text.indexOf("\n\n") : -1;
        String name = file.getFileName().toString().replaceFirst("\\.md$", "");
        return new Gathered(title == null ? name : title, slug(name.replaceFirst("^(doc|web|video|rec|slack)-", ""), 40),
                List.of(new Unit(body < 0 ? text : text.substring(body + 2), "sources", "file:" + file.getFileName(), LocalDate.now())));
    }

    // ---------------------------------------------------------------- formats

    static List<String> paragraphs(String text) {
        List<String> out = new ArrayList<>();
        for (String p : text.split("\\R\\s*\\R")) {
            String line = p.replaceAll("-\\R(?=\\p{Ll})", "").replaceAll("\\s*\\R\\s*", " ").replaceAll("\\s+", " ").strip();
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static List<String> pdfLines(PDDocument pdf) throws IOException {
        if (pdf.isEncrypted()) {
            throw new IOException("that PDF is password protected");
        }
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        List<String> lines = new ArrayList<>();
        int pages = Math.min(pdf.getNumberOfPages(), 400);
        for (int page = 1; page <= pages; page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            for (String paragraph : paragraphs(stripper.getText(pdf))) {
                lines.add("[page " + page + "] " + paragraph);
            }
        }
        return lines;
    }

    static List<String> htmlLines(Document doc) {
        doc.select("script, style, nav, footer, aside, form, noscript, svg, iframe").remove();
        List<String> lines = new ArrayList<>();
        String last = "";
        for (var element : doc.select("h1, h2, h3, h4, h5, h6, p, li, pre, blockquote, td, th, figcaption, dd, dt")) {
            String text = element.text().replaceAll("\\s+", " ").strip();
            if (text.length() >= 2 && !text.equals(last)) {
                lines.add(text);
                last = text;
            }
        }
        return lines;
    }

    static List<String> docxLines(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("word/document.xml");
            if (entry == null) {
                throw new IOException("that is not a Word (.docx) file");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return paragraphsOf(in, "w:p");
            }
        }
    }

    static List<String> pptxLines(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            List<? extends ZipEntry> slides = zip.stream()
                    .filter(e -> e.getName().matches("ppt/(slides/slide|notesSlides/notesSlide)\\d+\\.xml"))
                    .sorted(Comparator.comparingInt((ZipEntry e) -> number(e.getName())).thenComparing(e -> e.getName().contains("notesSlide")))
                    .toList();
            if (slides.isEmpty()) {
                throw new IOException("that is not a PowerPoint (.pptx) file");
            }
            List<String> lines = new ArrayList<>();
            for (ZipEntry entry : slides) {
                String label = entry.getName().contains("notesSlide") ? "slide " + number(entry.getName()) + " notes" : "slide " + number(entry.getName());
                try (InputStream in = zip.getInputStream(entry)) {
                    for (String paragraph : paragraphsOf(in, "a:p")) {
                        lines.add("[" + label + "] " + paragraph);
                    }
                }
            }
            return lines;
        }
    }

    /** Paragraph text from an Office XML part. External entities and DTDs are switched off. */
    private static List<String> paragraphsOf(InputStream xml, String paragraphTag) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(xml);
            NodeList paragraphs = doc.getElementsByTagName(paragraphTag);
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < paragraphs.getLength(); i++) {
                StringBuilder text = new StringBuilder();
                collect(paragraphs.item(i), text);
                String line = text.toString().replaceAll("\\s+", " ").strip();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException e) {
            throw new IOException("the document's XML could not be read: " + e.getMessage());
        }
    }

    private static void collect(Node node, StringBuilder out) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String name = ((Element) node).getTagName();
            if (name.equals("w:tab") || name.equals("w:br") || name.equals("a:br")) {
                out.append(' ');
            }
        }
        if (node.getNodeType() == Node.TEXT_NODE && node.getParentNode() != null) {
            String parent = ((Element) node.getParentNode()).getTagName();
            if (parent.equals("w:t") || parent.equals("a:t")) {
                out.append(node.getTextContent());
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            collect(child, out);
        }
    }

    static List<String> subtitleLines(String text) {
        List<String> lines = new ArrayList<>();
        String stamp = null;
        StringBuilder cue = new StringBuilder();
        for (String raw : (text + "\n\n").split("\\R", -1)) {
            Matcher time = CUE_TIME.matcher(raw);
            if (time.matches()) {
                stamp = "%02d:%s:%s".formatted(time.group(1) == null ? 0 : Integer.parseInt(time.group(1)), time.group(2), time.group(3));
                cue.setLength(0);
            } else if (raw.isBlank()) {
                if (stamp != null && cue.length() > 0) {
                    lines.add("[" + stamp + "] " + cue.toString().strip());
                }
                stamp = null;
                cue.setLength(0);
            } else if (stamp != null) {
                cue.append(raw.replaceAll("<[^>]+>", "")).append(' ');
            }
        }
        return lines;
    }

    private static List<String> ocrLines(Path image) throws IOException, InterruptedException {
        return paragraphs(Ingest.exec(List.of("tesseract", image.toString(), "stdout", "--psm", "3"), 120));
    }

    // ---------------------------------------------------------------- small helpers

    static String slug(String text, int max) {
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > max) {
            s = s.substring(0, max).replaceAll("-+$", "");
        }
        return s.isEmpty() ? "source" : s;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static int number(String entryName) {
        Matcher m = Pattern.compile("(\\d+)\\.xml$").matcher(entryName);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String relative(Path home, Path target) {
        try {
            return home.relativize(target).toString().isEmpty() ? "." : home.relativize(target).toString();
        } catch (IllegalArgumentException e) {
            return target.toString();
        }
    }

    private static LocalDate modified(Path file) throws IOException {
        return LocalDate.ofInstant(Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()), ZoneId.systemDefault());
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Tiny helper: does any path segment appear in {@code names}? */
    private static final class Paths {
        static boolean noneMatch(Path relative, Set<String> names) {
            for (Path part : relative) {
                if (names.contains(part.toString())) {
                    return false;
                }
            }
            return true;
        }
    }
}
