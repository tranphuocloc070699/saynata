package com.englishtranslation.backend.aipicture;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Draws a picture with the Codex CLI (`codex exec`) and the ChatGPT login in CODEX_HOME. Port of CodexDrawer in
 * pipeline/src/pipeline/media_ai.py (TASK-82). Codex prints "session id: <uuid>" and saves its pictures in
 * $CODEX_HOME/generated_images/<uuid>/, so the session id is the way to find our picture.
 *
 * Safety: the program starts with an EMPTY environment plus only PATH, HOME, CODEX_HOME and LANG. The backend's own
 * environment holds the Claude key, the database password and the JWT secrets; none of them reaches Codex.
 * The sandbox is read-only by default, stdin is /dev/null (Codex waits for input forever otherwise), and the run
 * is stopped after the timeout.
 */
@Component
public class CodexImageDrawer implements ImageDrawer {

    private static final Logger log = LoggerFactory.getLogger(CodexImageDrawer.class);
    private static final Pattern SESSION_ID = Pattern.compile("session id:\\s*([0-9a-f-]{36})");
    private static final int MAX_OUTPUT_TO_READ = 20_000;
    /** The same words as CodexDrawer.STOP_WORDS in the pipeline. */
    private static final List<String> LOGIN_PROBLEMS = List.of("not logged in", "please log in", "codex login");

    static final String NOT_SIGNED_IN = "Codex is not signed in on the server. Copy auth.json again (doc-11).";
    static final String PLAN_LIMIT = "The ChatGPT plan limit is reached. Try again later.";

    private final AiPictureProperties properties;

    public CodexImageDrawer(AiPictureProperties properties) {
        this.properties = properties;
    }

    @Override
    public byte[] draw(String imagePrompt) throws DrawException {
        Path codexHome = Path.of(properties.codexHome());
        Path workDir = null;
        Path outputFile = null;
        try {
            workDir = Files.createTempDirectory("saynata-codex-");
            outputFile = Files.createTempFile("saynata-codex-", ".log");
            ProcessBuilder builder = new ProcessBuilder(command(properties, AiPrompts.codexPrompt(imagePrompt)));
            builder.directory(workDir.toFile());
            Map<String, String> environment = builder.environment();
            environment.clear();
            environment.putAll(cleanEnvironment(System.getenv(), properties.codexHome()));
            builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());

            Process process = builder.start();
            if (!process.waitFor(properties.timeout().toSeconds(), TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new DrawException("Codex took too long (more than " + properties.timeout().toSeconds()
                        + " seconds) and was stopped. Try again.");
            }
            String output = tail(Files.readString(outputFile, StandardCharsets.UTF_8));
            Optional<Path> picture = newestPicture(codexHome, output);
            if (picture.isEmpty()) {
                log.warn("Codex made no picture (exit {}): {}", process.exitValue(), output);
                throw new DrawException(problemText(output, process.exitValue()));
            }
            return Files.readAllBytes(picture.get());
        } catch (IOException e) {
            throw new DrawException("Could not run Codex on the server: " + e.getMessage()
                    + ". Is it installed (AI_PICTURES_ENABLED, doc-11)?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DrawException("The server stopped before the picture was drawn.", e);
        } finally {
            deleteQuietly(outputFile);
            deleteQuietly(workDir);
        }
    }

    /** codex exec --skip-git-repo-check --sandbox <sandbox> <prompt>. Never a "danger" flag. */
    static List<String> command(AiPictureProperties properties, String prompt) {
        List<String> command = new ArrayList<>();
        command.add(properties.codexCommand());
        command.add("exec");
        command.add("--skip-git-repo-check");
        command.add("--sandbox");
        command.add(properties.sandbox());
        command.add(prompt);
        return command;
    }

    /**
     * The only variables Codex gets: PATH (to find itself and its helpers), HOME, CODEX_HOME (the login) and LANG.
     * {@code parent} is the backend's own environment; nothing else is copied from it.
     */
    static Map<String, String> cleanEnvironment(Map<String, String> parent, String codexHome) {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("PATH", parent.getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin"));
        environment.put("HOME", parent.getOrDefault("HOME", System.getProperty("user.home", "/tmp")));
        environment.put("CODEX_HOME", codexHome);
        environment.put("LANG", parent.getOrDefault("LANG", "C.UTF-8"));
        return environment;
    }

    /** The newest PNG in $codexHome/generated_images/<session id>/, where the session id is read from Codex's output. */
    static Optional<Path> newestPicture(Path codexHome, String output) {
        Matcher session = SESSION_ID.matcher(output);
        if (!session.find()) {
            return Optional.empty();
        }
        Path folder = codexHome.resolve("generated_images").resolve(session.group(1));
        if (!Files.isDirectory(folder)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .max(Comparator.comparingLong(CodexImageDrawer::modified));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** A plain sentence for the admin when Codex made no picture. */
    static String problemText(String output, int exitCode) {
        String lower = output.toLowerCase(Locale.ROOT);
        if (lower.contains("usage limit")) {
            return PLAN_LIMIT;
        }
        if (LOGIN_PROBLEMS.stream().anyMatch(lower::contains)) {
            return NOT_SIGNED_IN;
        }
        String last = output.strip();
        if (last.length() > 300) {
            last = last.substring(last.length() - 300);
        }
        return "Codex made no picture (exit " + exitCode + ")" + (last.isEmpty() ? "." : ": " + last);
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String tail(String text) {
        return text.length() <= MAX_OUTPUT_TO_READ ? text : text.substring(text.length() - MAX_OUTPUT_TO_READ);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.walk(path)) {
                    files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException | RuntimeException ignored) {
            // a temp file we cannot delete is not worth failing for
        }
    }
}
