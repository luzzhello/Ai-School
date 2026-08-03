package org.ruoyi.service.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ruoyi.config.LitPaperProperties;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CnkiCrawlerProcessClientTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsCrawlerCommandFromPropertiesAndArguments() {
        LitPaperProperties properties = properties();
        CnkiCrawlerProcessClient client = new CnkiCrawlerProcessClient(properties);
        Path output = tempDir.resolve("papers.jsonl");
        Path checkpoint = tempDir.resolve("checkpoint.json");

        List<String> command = client.buildCommand(
            List.of("人工智能", "知识图谱"), 12, true, output, checkpoint);

        assertEquals(List.of(
            "python-custom",
            "-m", "src",
            "--config", "crawler.yaml",
            "crawl-task",
            "--keywords", "人工智能,知识图谱",
            "--max-per-keyword", "12",
            "--output", output.toString(),
            "--checkpoint", checkpoint.toString(),
            "--search-lang", "chinese",
            "--list-only"
        ), command);
    }

    @Test
    void buildsBilingualCommandWithEnOutputs() {
        LitPaperProperties properties = properties();
        CnkiCrawlerProcessClient client = new CnkiCrawlerProcessClient(properties);
        Path outputZh = tempDir.resolve("papers_zh.jsonl");
        Path outputEn = tempDir.resolve("papers_en.jsonl");
        Path checkpointZh = tempDir.resolve("checkpoint_zh.json");
        Path checkpointEn = tempDir.resolve("checkpoint_en.json");
        Path keywordsFile = tempDir.resolve("keywords.txt");

        List<String> command = client.buildCommand(
            keywordsFile, 10, true, "both",
            outputZh, checkpointZh, outputEn, checkpointEn);

        assertEquals(List.of(
            "python-custom",
            "-m", "src",
            "--config", "crawler.yaml",
            "crawl-task",
            "--keywords-file", keywordsFile.toString(),
            "--max-per-keyword", "10",
            "--output", outputZh.toString(),
            "--checkpoint", checkpointZh.toString(),
            "--search-lang", "both",
            "--output-en", outputEn.toString(),
            "--checkpoint-en", checkpointEn.toString(),
            "--list-only"
        ), command);
    }

    @Test
    void runsProcessWritesKeywordsFileAndForcesUtf8Env() throws Exception {
        LitPaperProperties properties = properties();
        CnkiCrawlerProcessClient client = new CnkiCrawlerProcessClient(properties);
        Path output = tempDir.resolve("result.jsonl");
        Path checkpoint = tempDir.resolve("checkpoint.json");
        client.setProcessStarter(processBuilder -> {
            assertEquals("utf-8", processBuilder.environment().get("PYTHONIOENCODING"));
            assertEquals("1", processBuilder.environment().get("PYTHONUTF8"));
            assertTrue(processBuilder.command().contains("--keywords-file"));
            int fileIndex = processBuilder.command().indexOf("--keywords-file") + 1;
            Path keywordsFile = Path.of(processBuilder.command().get(fileIndex));
            assertEquals("人工智能\n", Files.readString(keywordsFile, StandardCharsets.UTF_8));
            int outputIndex = processBuilder.command().indexOf("--output") + 1;
            Files.writeString(Path.of(processBuilder.command().get(outputIndex)),
                "{\"title\":\"demo\"}\n", StandardCharsets.UTF_8);
            return new CompletedProcess("keyword=人工智能\n", 0);
        });

        CnkiCrawlerProcessClient.CrawlTaskResult result = client.runCrawlTask(
            List.of("人工智能"), 3, false, output, checkpoint, Duration.ofSeconds(2));

        assertEquals(0, result.exitCode());
        assertTrue(result.logTail().contains("人工智能"));
    }

    @Test
    void runsProcessWritesOutputAndKeepsOnlyLogTail() throws Exception {
        LitPaperProperties properties = properties();
        CnkiCrawlerProcessClient client = new CnkiCrawlerProcessClient(properties);
        Path output = tempDir.resolve("result.jsonl");
        Path checkpoint = tempDir.resolve("checkpoint.json");
        String logs = "prefix-marker-" + "x".repeat(10000) + "-tail-marker";
        client.setProcessStarter(processBuilder -> {
            assertEquals(tempDir.toFile(), processBuilder.directory());
            assertTrue(processBuilder.redirectErrorStream());
            int outputIndex = processBuilder.command().indexOf("--output") + 1;
            Files.writeString(Path.of(processBuilder.command().get(outputIndex)),
                "{\"title\":\"demo\"}\n", StandardCharsets.UTF_8);
            return new CompletedProcess(logs, 0);
        });

        CnkiCrawlerProcessClient.CrawlTaskResult result = client.runCrawlTask(
            List.of("人工智能"), 3, false, output, checkpoint, Duration.ofSeconds(2));

        assertEquals(0, result.exitCode());
        assertEquals(output, result.jsonlPath());
        assertEquals("{\"title\":\"demo\"}\n", Files.readString(output));
        assertTrue(result.logTail().getBytes(StandardCharsets.UTF_8).length <= 8192);
        assertFalse(result.logTail().contains("prefix-marker"));
        assertTrue(result.logTail().endsWith("-tail-marker"));
    }

    @Test
    void forciblyDestroysProcessWhenTimeoutExpires() {
        LitPaperProperties properties = properties();
        CnkiCrawlerProcessClient client = new CnkiCrawlerProcessClient(properties);
        NeverEndingProcess process = new NeverEndingProcess();
        client.setProcessStarter(processBuilder -> process);

        assertThrows(TimeoutException.class, () -> client.runCrawlTask(
            List.of("人工智能"), 3, false,
            tempDir.resolve("result.jsonl"),
            tempDir.resolve("checkpoint.json"),
            Duration.ofMillis(1)));
        assertTrue(process.destroyedForcibly);
    }

    private LitPaperProperties properties() {
        LitPaperProperties properties = new LitPaperProperties();
        properties.getOndemand().setPythonExecutable("python-custom");
        properties.getOndemand().setCrawlerWorkDir(tempDir.toString());
        properties.getOndemand().setConfigPath("crawler.yaml");
        return properties;
    }

    private static class CompletedProcess extends Process {

        private final InputStream inputStream;
        private final int exitCode;

        private CompletedProcess(String output, int exitCode) {
            this.inputStream = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
        }
    }

    private static final class NeverEndingProcess extends CompletedProcess {

        private boolean destroyedForcibly;

        private NeverEndingProcess() {
            super("", 0);
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public Process destroyForcibly() {
            destroyedForcibly = true;
            return this;
        }
    }
}
