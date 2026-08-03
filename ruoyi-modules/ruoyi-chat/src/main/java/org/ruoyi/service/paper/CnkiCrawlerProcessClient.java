package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.config.LitPaperProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 启动 CNKI Python 爬虫并收集执行结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CnkiCrawlerProcessClient {

    /** 保留给任务错误回显的日志尾部容量 */
    private static final int LOG_TAIL_BYTES = 8192;

    private final LitPaperProperties litPaperProperties;
    private ProcessStarter processStarter = ProcessBuilder::start;

    public record CrawlTaskResult(int exitCode, Path jsonlPath, Path jsonlPathEn, String logTail) {
    }

    public CrawlTaskResult runCrawlTask(
        List<String> keywords,
        int maxPerKeyword,
        boolean listOnly,
        Path outputJsonl,
        Path checkpoint,
        Duration timeout
    ) throws Exception {
        return runCrawlTask(keywords, maxPerKeyword, listOnly, "chinese",
            outputJsonl, checkpoint, null, null, timeout);
    }

    /**
     * @param searchLang {@code chinese} / {@code foreign} / {@code both}（中英各爬相同 maxPerKeyword）
     * @param outputJsonlEn {@code both} 时外文 JSONL
     * @param checkpointEn  {@code both} 时外文断点
     */
    public CrawlTaskResult runCrawlTask(
        List<String> keywords,
        int maxPerKeyword,
        boolean listOnly,
        String searchLang,
        Path outputJsonl,
        Path checkpoint,
        Path outputJsonlEn,
        Path checkpointEn,
        Duration timeout
    ) throws Exception {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }

        // Windows 下命令行传中文可能乱码；改为 UTF-8 关键词文件传递
        Path keywordsFile = outputJsonl.getParent() != null
            ? outputJsonl.getParent().resolve("keywords.txt")
            : Path.of("keywords-" + System.nanoTime() + ".txt");
        writeKeywordsFile(keywordsFile, keywords);

        List<String> command = buildCommand(keywordsFile, maxPerKeyword, listOnly, searchLang,
            outputJsonl, checkpoint, outputJsonlEn, checkpointEn);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(resolveWorkDir().toFile());
        processBuilder.redirectErrorStream(true);
        // 强制 Python stdout/stderr 用 UTF-8，避免 Windows 默认 GBK 被 Java 按 UTF-8 读成乱码
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("PYTHONUTF8", "1");

        log.info(
            "[cnki-crawler] start workDir={} keywords={} keywordsFile={} maxPerKeyword={} listOnly={} searchLang={} timeout={}s cmd={}",
            processBuilder.directory().getAbsolutePath(),
            keywords,
            keywordsFile,
            maxPerKeyword,
            listOnly,
            searchLang,
            timeout.toSeconds(),
            String.join(" ", command));

        Process process = processStarter.start(processBuilder);
        LogTailBuffer logTail = new LogTailBuffer(LOG_TAIL_BYTES);
        AtomicReference<IOException> readFailure = new AtomicReference<>();
        Thread logReader = startLogReader(process.getInputStream(), logTail, readFailure);

        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            logReader.join(1000);
            log.warn("[cnki-crawler] timed out after {}, destroying process; logTail=\n{}",
                timeout, logTail.asString());
            throw new TimeoutException("CNKI crawl task timed out after " + timeout);
        }

        logReader.join();
        if (readFailure.get() != null) {
            throw readFailure.get();
        }
        int exitCode = process.exitValue();
        String tail = logTail.asString();
        if (exitCode != 0) {
            log.warn("[cnki-crawler] exit={} logTail=\n{}", exitCode, tail);
        } else {
            log.info("[cnki-crawler] exit=0 logTailBytes={}", tail.getBytes(StandardCharsets.UTF_8).length);
        }
        return new CrawlTaskResult(exitCode, outputJsonl, outputJsonlEn, tail);
    }

    List<String> buildCommand(
        List<String> keywords,
        int maxPerKeyword,
        boolean listOnly,
        Path outputJsonl,
        Path checkpoint
    ) {
        // 兼容旧测试：仍接受关键词列表，运行时会再写成文件
        return buildCommandFromKeywordsCsv(
            String.join(",", keywords), maxPerKeyword, listOnly, "chinese",
            outputJsonl, checkpoint, null, null);
    }

    List<String> buildCommand(
        Path keywordsFile,
        int maxPerKeyword,
        boolean listOnly,
        String searchLang,
        Path outputJsonl,
        Path checkpoint,
        Path outputJsonlEn,
        Path checkpointEn
    ) {
        Objects.requireNonNull(keywordsFile, "keywordsFile");
        Objects.requireNonNull(outputJsonl, "outputJsonl");
        Objects.requireNonNull(checkpoint, "checkpoint");
        String lang = searchLang == null || searchLang.isBlank() ? "chinese" : searchLang.trim();

        LitPaperProperties.OnDemand onDemand = litPaperProperties.getOndemand();
        List<String> command = new ArrayList<>();
        command.add(onDemand.getPythonExecutable());
        command.add("-m");
        command.add("src");
        command.add("--config");
        command.add(onDemand.getConfigPath());
        command.add("crawl-task");
        command.add("--keywords-file");
        command.add(keywordsFile.toString());
        command.add("--max-per-keyword");
        command.add(String.valueOf(maxPerKeyword));
        command.add("--output");
        command.add(outputJsonl.toString());
        command.add("--checkpoint");
        command.add(checkpoint.toString());
        command.add("--search-lang");
        command.add(lang);
        if ("both".equalsIgnoreCase(lang)) {
            Objects.requireNonNull(outputJsonlEn, "outputJsonlEn");
            Objects.requireNonNull(checkpointEn, "checkpointEn");
            command.add("--output-en");
            command.add(outputJsonlEn.toString());
            command.add("--checkpoint-en");
            command.add(checkpointEn.toString());
        }
        if (listOnly) {
            command.add("--list-only");
        }
        return command;
    }

    /** 仅用于兼容旧单测断言（命令行 CSV 形式）。 */
    List<String> buildCommand(
        List<String> keywords,
        int maxPerKeyword,
        boolean listOnly,
        String searchLang,
        Path outputJsonl,
        Path checkpoint,
        Path outputJsonlEn,
        Path checkpointEn
    ) {
        return buildCommandFromKeywordsCsv(
            String.join(",", keywords), maxPerKeyword, listOnly, searchLang,
            outputJsonl, checkpoint, outputJsonlEn, checkpointEn);
    }

    private List<String> buildCommandFromKeywordsCsv(
        String keywordsCsv,
        int maxPerKeyword,
        boolean listOnly,
        String searchLang,
        Path outputJsonl,
        Path checkpoint,
        Path outputJsonlEn,
        Path checkpointEn
    ) {
        Objects.requireNonNull(keywordsCsv, "keywordsCsv");
        Objects.requireNonNull(outputJsonl, "outputJsonl");
        Objects.requireNonNull(checkpoint, "checkpoint");
        String lang = searchLang == null || searchLang.isBlank() ? "chinese" : searchLang.trim();

        LitPaperProperties.OnDemand onDemand = litPaperProperties.getOndemand();
        List<String> command = new ArrayList<>();
        command.add(onDemand.getPythonExecutable());
        command.add("-m");
        command.add("src");
        command.add("--config");
        command.add(onDemand.getConfigPath());
        command.add("crawl-task");
        command.add("--keywords");
        command.add(keywordsCsv);
        command.add("--max-per-keyword");
        command.add(String.valueOf(maxPerKeyword));
        command.add("--output");
        command.add(outputJsonl.toString());
        command.add("--checkpoint");
        command.add(checkpoint.toString());
        command.add("--search-lang");
        command.add(lang);
        if ("both".equalsIgnoreCase(lang)) {
            Objects.requireNonNull(outputJsonlEn, "outputJsonlEn");
            Objects.requireNonNull(checkpointEn, "checkpointEn");
            command.add("--output-en");
            command.add(outputJsonlEn.toString());
            command.add("--checkpoint-en");
            command.add(checkpointEn.toString());
        }
        if (listOnly) {
            command.add("--list-only");
        }
        return command;
    }

    static void writeKeywordsFile(Path keywordsFile, List<String> keywords) throws IOException {
        Objects.requireNonNull(keywordsFile, "keywordsFile");
        Objects.requireNonNull(keywords, "keywords");
        StringBuilder sb = new StringBuilder();
        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty()) {
                sb.append(trimmed).append('\n');
            }
        }
        Path parent = keywordsFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(keywordsFile, sb.toString(), StandardCharsets.UTF_8);
    }

    void setProcessStarter(ProcessStarter processStarter) {
        this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
    }

    private Path resolveWorkDir() {
        Path workDir = Path.of(litPaperProperties.getOndemand().getCrawlerWorkDir());
        if (!workDir.isAbsolute()) {
            workDir = Path.of(System.getProperty("user.dir")).resolve(workDir);
        }
        return workDir.normalize().toAbsolutePath();
    }

    private Thread startLogReader(
        InputStream inputStream,
        LogTailBuffer logTail,
        AtomicReference<IOException> readFailure
    ) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[cnki-crawler] {}", line);
                    byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                    logTail.append(bytes, bytes.length);
                }
            } catch (IOException exception) {
                readFailure.set(exception);
            }
        }, "cnki-crawler-log-reader");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @FunctionalInterface
    interface ProcessStarter {

        Process start(ProcessBuilder processBuilder) throws IOException;
    }

    private static final class LogTailBuffer {

        private final byte[] bytes;
        private int start;
        private int size;

        private LogTailBuffer(int capacity) {
            this.bytes = new byte[capacity];
        }

        private void append(byte[] source, int length) {
            for (int index = 0; index < length; index++) {
                if (size < bytes.length) {
                    bytes[(start + size) % bytes.length] = source[index];
                    size++;
                } else {
                    bytes[start] = source[index];
                    start = (start + 1) % bytes.length;
                }
            }
        }

        private String asString() {
            byte[] result = new byte[size];
            for (int index = 0; index < size; index++) {
                result[index] = bytes[(start + index) % bytes.length];
            }
            return new String(result, StandardCharsets.UTF_8);
        }
    }
}
