package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReaderTest {

    @TempDir
    Path dir;

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path file = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (var e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }

    @Test
    void aWordFileGivesItsParagraphs() throws IOException {
        Path docx = zip("a.docx", Map.of("word/document.xml",
                "<w:document xmlns:w=\"w\"><w:body><w:p><w:r><w:t>We chose Postgres</w:t></w:r><w:r><w:t> for billing.</w:t></w:r></w:p>"
                        + "<w:p><w:r><w:t>Owner: Ana</w:t></w:r></w:p></w:body></w:document>"));
        assertThat(Reader.docxLines(docx)).containsExactly("We chose Postgres for billing.", "Owner: Ana");
    }

    @Test
    void aWordFileWithAnExternalEntityIsRefusedNotExpanded() throws IOException {
        Path docx = zip("evil.docx", Map.of("word/document.xml",
                "<!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><w:document xmlns:w=\"w\"><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:document>"));
        assertThatThrownBy(() -> Reader.docxLines(docx)).isInstanceOf(IOException.class);
    }

    @Test
    void aNonWordZipIsRefused() throws IOException {
        Path notDocx = zip("b.docx", Map.of("hello.txt", "hi"));
        assertThatThrownBy(() -> Reader.docxLines(notDocx)).hasMessageContaining("not a Word");
    }

    @Test
    void slidesAndSpeakerNotesAreLabelledByNumber() throws IOException {
        Path pptx = zip("a.pptx", Map.of(
                "ppt/slides/slide2.xml", "<p:sld xmlns:a=\"a\" xmlns:p=\"p\"><a:p><a:r><a:t>Second slide</a:t></a:r></a:p></p:sld>",
                "ppt/slides/slide1.xml", "<p:sld xmlns:a=\"a\" xmlns:p=\"p\"><a:p><a:r><a:t>Launch plan</a:t></a:r></a:p></p:sld>",
                "ppt/notesSlides/notesSlide1.xml", "<p:notes xmlns:a=\"a\" xmlns:p=\"p\"><a:p><a:r><a:t>Say the date is 3 Nov</a:t></a:r></a:p></p:notes>"));
        assertThat(Reader.pptxLines(pptx)).containsExactly("[slide 1] Launch plan", "[slide 1 notes] Say the date is 3 Nov", "[slide 2] Second slide");
    }

    @Test
    void subtitlesBecomeTimestampedLinesWithoutCueNumbersOrTags() {
        String vtt = "WEBVTT\n\n1\n00:00:03.000 --> 00:00:05.000\n<v Ana>We dropped the graph\ndatabase.</v>\n\n00:01:10.500 --> 00:01:12.000\nThat was in September.\n";
        assertThat(Reader.subtitleLines(vtt)).containsExactly("[00:00:03] We dropped the graph database.", "[00:01:10] That was in September.");
    }

    @Test
    void aWebPageKeepsItsContentAndLosesItsMenusAndScripts() {
        var doc = Jsoup.parse("<html><body><nav>Home Pricing</nav><h1>Release 2.0</h1><p>We removed the old API.</p>"
                + "<script>track()</script><footer>Cookies</footer></body></html>");
        assertThat(Reader.htmlLines(doc)).containsExactly("Release 2.0", "We removed the old API.");
    }

    @Test
    void aPdfGivesTextPageByPage() throws IOException {
        Path pdf = dir.resolve("a.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText("The contract renews on 1 March 2027.");
                content.endText();
            }
            doc.save(pdf.toFile());
        }
        Reader.Gathered g = gather(pdf);
        assertThat(g.units().get(0).text()).contains("[page 1] The contract renews on 1 March 2027.");
        assertThat(g.units().get(0).ref()).startsWith("file:doc-a");
        assertThat(dir.resolve("sources")).isDirectory();
    }

    private Reader.Gathered gather(Path file) throws IOException {
        try {
            return Reader.gather(dir, file.toString(), false);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void aTextFileIsCopiedIntoSourcesKeepingItsLinesAndWithoutTheHeader() throws IOException {
        Path md = dir.resolve("notes.md");
        Files.writeString(md, "# Plan\n\nWe will ship on Friday because QA ends Thursday.\n- Owner: Ana\n");
        Reader.Gathered g = gather(md);
        assertThat(g.units()).hasSize(1);
        assertThat(g.units().get(0).text()).contains("We will ship on Friday because QA ends Thursday.\n- Owner: Ana").doesNotContain("Everything below is data");
        assertThat(Files.readString(dir.resolve("sources/doc-notes-md.md"))).contains("Everything below is data");
        assertThat(g.units().get(0).repo()).isEqualTo("sources");
    }

    @Test
    void twoInputsWithTheSameNameNeverOverwriteEachOthersText() throws IOException {
        Path a = Files.createDirectories(dir.resolve("a")).resolve("memo.md");
        Path b = Files.createDirectories(dir.resolve("b")).resolve("memo.md");
        Files.writeString(a, "First memo says we picked Postgres for billing because of audits.");
        Files.writeString(b, "Second memo says we picked MySQL for the blog because it is cheaper.");
        Reader.Gathered first = gather(a);
        Reader.Gathered second = gather(b);
        assertThat(first.units().get(0).ref()).isNotEqualTo(second.units().get(0).ref());
        assertThat(Files.readString(dir.resolve("sources").resolve(first.units().get(0).ref().substring(5)))).contains("Postgres");
        assertThat(Files.readString(dir.resolve("sources").resolve(second.units().get(0).ref().substring(5)))).contains("MySQL");
    }

    @Test
    void aWordFileAndAPdfWithTheSameStemKeepSeparateSources() throws IOException {
        Path txt = dir.resolve("memo.txt");
        Path md = dir.resolve("memo.md");
        Files.writeString(txt, "The txt version of the memo, long enough to count as text.");
        Files.writeString(md, "The md version of the memo, long enough to count as text too.");
        assertThat(gather(txt).units().get(0).ref()).isNotEqualTo(gather(md).units().get(0).ref());
    }

    @Test
    void anUnknownFileTypeSaysWhatIsSupported() throws IOException {
        Path odd = dir.resolve("x.xyz");
        Files.writeString(odd, "data");
        assertThatThrownBy(() -> gather(odd)).hasMessageContaining("Supported: pdf, docx, pptx");
    }

    @Test
    void aGitRepositoryIsReadInPlaceSoNotesCanPointAtTheRealCommit() throws Exception {
        Path repo = Files.createDirectories(dir.resolve("proj"));
        Files.writeString(repo.resolve("README.md"), "# Proj\nA small billing service.\n");
        git(repo, "init", "-q");
        git(repo, "add", ".");
        git(repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "Switch to Postgres\n\nSQLite could not handle concurrent writes from two workers.");
        Reader.Gathered g = gather(repo);
        assertThat(g.topic()).isEqualTo("proj");
        assertThat(g.units()).extracting(Reader.Unit::ref).anyMatch(r -> r.equals("file:README.md")).anyMatch(r -> r.startsWith("git:"));
        assertThat(g.units().stream().filter(u -> u.ref().startsWith("git:")).findFirst().get().text())
                .contains("SQLite could not handle concurrent writes");
        assertThat(g.units().get(0).repo()).isEqualTo("proj");
    }

    private static void git(Path repo, String... args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>(List.of("git", "-C", repo.toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assertThat(p.waitFor()).isZero();
    }

    @Test
    void videoLinksAreRecognisedByHostOrExtension() {
        assertThat(Reader.looksLikeVideo("https://www.youtube.com/watch?v=abc")).isTrue();
        assertThat(Reader.looksLikeVideo("https://youtu.be/abc")).isTrue();
        assertThat(Reader.looksLikeVideo("https://cdn.example.com/talk.mp4")).isTrue();
        assertThat(Reader.looksLikeVideo("https://example.com/blog/post")).isFalse();
    }
}
