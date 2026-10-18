package com.englishtranslation.backend.aipicture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Codex runner (TASK-91): the clean environment, the picture lookup, and a run with a fake `codex` script. */
class CodexImageDrawerTest {

    static final String SESSION = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    @TempDir
    Path dir;

    // ---- the clean environment ----

    @Test
    void theEnvironmentHasOnlyPathHomeCodexHomeAndLangAndNoSecrets() {
        Map<String, String> backendEnvironment = Map.of("PATH", "/usr/bin", "HOME", "/home/app", "LANG", "en_US.UTF-8",
                "CLAUDE_API_KEY", "sk-secret", "SPRING_DATASOURCE_PASSWORD", "db-secret", "ADMIN_JWT_SECRET", "jwt-secret",
                "RESEND_API_KEY", "re-secret", "AWS_SECRET_ACCESS_KEY", "aws-secret");

        Map<String, String> environment = CodexImageDrawer.cleanEnvironment(backendEnvironment, "/app/.codex");

        assertThat(environment).containsOnlyKeys("PATH", "HOME", "CODEX_HOME", "LANG");
        assertThat(environment).containsEntry("PATH", "/usr/bin").containsEntry("HOME", "/home/app")
                .containsEntry("CODEX_HOME", "/app/.codex").containsEntry("LANG", "en_US.UTF-8");
        assertThat(environment.values()).noneMatch(value -> value.contains("secret"));
    }

    @Test
    void theEnvironmentHasSafeDefaultsWhenTheBackendHasNone() {
        Map<String, String> environment = CodexImageDrawer.cleanEnvironment(Map.of(), "/app/.codex");

        assertThat(environment).containsOnlyKeys("PATH", "HOME", "CODEX_HOME", "LANG");
        assertThat(environment.get("PATH")).isNotBlank();
    }

    @Test
    void theCommandUsesTheConfiguredSandboxAndNeverADangerFlag() {
        List<String> command = CodexImageDrawer.command(properties("codex", "read-only", Duration.ofMinutes(10)), "PROMPT");

        assertThat(command).containsExactly("codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only", "PROMPT");
        assertThat(String.join(" ", command)).doesNotContain("danger").doesNotContain("bypass");
    }

    // ---- finding the picture ----

    @Test
    void thePictureIsTheNewestPngOfTheSessionFolder() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("generated_images").resolve(SESSION));
        Path old = Files.write(folder.resolve("old.png"), new byte[] {1});
        Path newest = Files.write(folder.resolve("new.png"), new byte[] {2});
        Files.write(folder.resolve("notes.txt"), new byte[] {3});
        Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(newest, java.nio.file.attribute.FileTime.fromMillis(2_000));
        // Another session's picture must not be taken.
        Path other = Files.createDirectories(dir.resolve("generated_images").resolve("11111111-1111-4111-8111-111111111111"));
        Files.setLastModifiedTime(Files.write(other.resolve("other.png"), new byte[] {4}),
                java.nio.file.attribute.FileTime.fromMillis(9_000));

        Optional<Path> found = CodexImageDrawer.newestPicture(dir, "OpenAI Codex\nsession id: " + SESSION + "\nworking...");

        assertThat(found).contains(newest);
    }

    @Test
    void noSessionIdOrNoFolderMeansNoPicture() throws Exception {
        assertThat(CodexImageDrawer.newestPicture(dir, "no session here")).isEmpty();
        assertThat(CodexImageDrawer.newestPicture(dir, "session id: " + SESSION)).isEmpty();
        Files.createDirectories(dir.resolve("generated_images").resolve(SESSION)); // folder without a PNG
        assertThat(CodexImageDrawer.newestPicture(dir, "session id: " + SESSION)).isEmpty();
    }

    @Test
    void errorTextsAreSayingWhatToDo() {
        assertThat(CodexImageDrawer.problemText("You've hit your usage limit.", 1)).isEqualTo(CodexImageDrawer.PLAN_LIMIT);
        assertThat(CodexImageDrawer.problemText("Not logged in", 1)).isEqualTo(CodexImageDrawer.NOT_SIGNED_IN);
        assertThat(CodexImageDrawer.problemText("Please log in again", 1)).isEqualTo(CodexImageDrawer.NOT_SIGNED_IN);
        assertThat(CodexImageDrawer.problemText("run `codex login` first", 1)).isEqualTo(CodexImageDrawer.NOT_SIGNED_IN);
        assertThat(CodexImageDrawer.problemText("something odd", 2)).contains("exit 2").contains("something odd");
    }

    // ---- a real run with a fake codex program ----

    @Test
    void aRunWithAFakeCodexReturnsThePngAndStartsCleanly() throws Exception {
        assumeThat(Files.isExecutable(Path.of("/bin/sh"))).isTrue();
        Path home = Files.createDirectories(dir.resolve("codex-home"));
        Path png = Files.write(dir.resolve("picture.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
        Path argsFile = dir.resolve("args.txt");
        Path envFile = dir.resolve("env.txt");
        Path stdinFile = dir.resolve("stdin.txt");
        Path script = fakeCodex("""
                #!/bin/sh
                printf '%%s\\n' "$@" > '%1$s'
                env > '%2$s'
                cat > '%3$s'
                mkdir -p "$CODEX_HOME/generated_images/%4$s"
                cp '%5$s' "$CODEX_HOME/generated_images/%4$s/ig_1.png"
                echo "session id: %4$s"
                """.formatted(argsFile, envFile, stdinFile, SESSION, png));
        CodexImageDrawer drawer = new CodexImageDrawer(
                properties(script.toString(), "read-only", home.toString(), Duration.ofSeconds(30)));

        byte[] drawn = drawer.draw("A red apple. Style: flat.");

        assertThat(drawn).isEqualTo(Files.readAllBytes(png));
        List<String> args = Files.readAllLines(argsFile);
        assertThat(args.subList(0, 4)).containsExactly("exec", "--skip-git-repo-check", "--sandbox", "read-only");
        assertThat(String.join("\n", args)).contains("Use your built-in image generation tool exactly ONCE")
                .contains("IMAGE PROMPT:").contains("A red apple. Style: flat.");
        // stdin was /dev/null: the script's `cat` ended at once.
        assertThat(Files.readString(stdinFile)).isEmpty();
        // The environment: only what we gave. The Maven/JVM process has many more variables (JAVA_HOME, USER, ...).
        Map<String, String> seen = new java.util.HashMap<>();
        for (String line : Files.readAllLines(envFile)) {
            int equals = line.indexOf('=');
            seen.put(line.substring(0, equals), line.substring(equals + 1));
        }
        assertThat(seen).containsEntry("CODEX_HOME", home.toString());
        seen.keySet().removeAll(List.of("PATH", "HOME", "CODEX_HOME", "LANG", "PWD", "OLDPWD", "SHLVL", "_"));
        assertThat(seen).isEmpty();
    }

    @Test
    void aRunThatMakesNoPictureExplainsWhy() throws Exception {
        assumeThat(Files.isExecutable(Path.of("/bin/sh"))).isTrue();
        Path script = fakeCodex("#!/bin/sh\necho 'ERROR: not logged in'\nexit 1\n");
        CodexImageDrawer drawer = new CodexImageDrawer(properties(script.toString(), "read-only",
                dir.resolve("codex-home").toString(), Duration.ofSeconds(30)));

        assertThatThrownBy(() -> drawer.draw("x")).isInstanceOf(DrawException.class)
                .hasMessage(CodexImageDrawer.NOT_SIGNED_IN);
    }

    @Test
    void aRunThatNeverEndsIsStoppedAfterTheTimeout() throws Exception {
        assumeThat(Files.isExecutable(Path.of("/bin/sh"))).isTrue();
        Path script = fakeCodex("#!/bin/sh\nsleep 60\n");
        CodexImageDrawer drawer = new CodexImageDrawer(properties(script.toString(), "read-only",
                dir.resolve("codex-home").toString(), Duration.ofSeconds(1)));

        long started = System.currentTimeMillis();
        assertThatThrownBy(() -> drawer.draw("x")).isInstanceOf(DrawException.class).hasMessageContaining("took too long");
        assertThat(System.currentTimeMillis() - started).isLessThan(20_000);
    }

    @Test
    void aMissingCodexProgramIsAPlainError() {
        CodexImageDrawer drawer = new CodexImageDrawer(properties("/no/such/codex", "read-only",
                dir.resolve("codex-home").toString(), Duration.ofSeconds(5)));

        assertThatThrownBy(() -> drawer.draw("x")).isInstanceOf(DrawException.class)
                .hasMessageContaining("Could not run Codex");
    }

    // ---- helpers ----

    private Path fakeCodex(String text) throws IOException {
        Path script = dir.resolve("fake-codex-" + System.nanoTime());
        Files.writeString(script, text, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    private static AiPictureProperties properties(String command, String sandbox, Duration timeout) {
        return properties(command, sandbox, "/tmp/codex-home", timeout);
    }

    private static AiPictureProperties properties(String command, String sandbox, String codexHome, Duration timeout) {
        return new AiPictureProperties(true, 30, BigDecimal.ONE, command, codexHome, sandbox, timeout);
    }
}
