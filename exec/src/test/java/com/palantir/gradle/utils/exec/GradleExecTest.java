package com.palantir.gradle.utils.exec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecSpec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleExecTest {

    @TempDir
    Path tempDir;

    private GradleExec gradleExec;

    @BeforeEach
    void setUp() {
        gradleExec = ProjectBuilder.builder().build().getObjects().newInstance(GradleExec.class);
    }

    @Test
    void lazyExec_shouldReturnProvider() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("echo", "test");

        // When
        Provider<GradleExec.ExecResultWithOutput> resultProvider = gradleExec.lazyExec(action);

        // Then
        assertThat(resultProvider).isNotNull();
        // Provider should not execute until .get() is called
        assertThat(resultProvider.isPresent()).isTrue();
    }

    @Test
    void lazyExec_shouldDeferExecution() throws IOException {
        // Given - a command that creates a file as a side effect
        Path sideEffectFile = tempDir.resolve("side-effect.txt");
        Action<ExecSpec> action = spec -> {
            spec.commandLine("sh", "-c", "echo 'executed' > " + sideEffectFile);
        };

        // When - create the provider
        Provider<GradleExec.ExecResultWithOutput> resultProvider = gradleExec.lazyExec(action);

        // Then - side effect should not have occurred yet
        assertThat(Files.exists(sideEffectFile)).isFalse();

        // When - actually get the result
        resultProvider.get();

        // Then - now the side effect should have occurred
        assertThat(Files.exists(sideEffectFile)).isTrue();
        assertThat(Files.readString(sideEffectFile).trim()).isEqualTo("executed");
    }

    @Test
    void exec_shouldExecuteImmediately() throws IOException {
        // Given
        Path sideEffectFile = tempDir.resolve("side-effect.txt");
        Action<ExecSpec> action = spec -> {
            spec.commandLine("sh", "-c", "echo 'executed' > " + sideEffectFile);
        };

        // When
        gradleExec.exec(action);

        // Then
        assertThat(Files.exists(sideEffectFile)).isTrue();
        assertThat(Files.readString(sideEffectFile).trim()).isEqualTo("executed");
    }

    @Test
    void execWithFileOutput_shouldWriteToFiles() throws IOException {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("echo", "file content");
        Path stdoutFile = tempDir.resolve("stdout.txt");
        Path stderrFile = tempDir.resolve("stderr.txt");

        // When
        gradleExec.execWithFileOutput(action, stdoutFile, stderrFile);

        // Then
        assertThat(Files.exists(stdoutFile)).isTrue();
        assertThat(Files.exists(stderrFile)).isTrue();
        assertThat(Files.readString(stdoutFile).trim()).isEqualTo("file content");
        assertThat(Files.readString(stderrFile)).isEmpty();
    }

    @Test
    void execWithFileOutput_shouldCallAssertNormalExitValue() {
        // Given
        Action<ExecSpec> action = spec -> {
            spec.commandLine("sh", "-c", "exit 1");
        };
        Path stdoutFile = tempDir.resolve("stdout.txt");
        Path stderrFile = tempDir.resolve("stderr.txt");

        // When & Then
        assertThatThrownBy(() -> gradleExec.execWithFileOutput(action, stdoutFile, stderrFile))
                .isInstanceOf(RuntimeException.class);

        // Files should not be created when execution fails
        assertThat(Files.exists(stdoutFile)).isFalse();
        assertThat(Files.exists(stderrFile)).isFalse();
    }

    @Test
    void execWithFileOutput_shouldHandleIoException() throws IOException {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("echo", "test");
        Path readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectory(readOnlyDir);
        readOnlyDir.toFile().setReadOnly();

        Path invalidStdoutFile = readOnlyDir.resolve("stdout.txt");
        Path stderrFile = tempDir.resolve("stderr.txt");

        // When & Then
        assertThatThrownBy(() -> gradleExec.execWithFileOutput(action, invalidStdoutFile, stderrFile))
                .isInstanceOf(IOException.class);
    }

    @Test
    void lazyExec_shouldChainProvidersCorrectly() {
        // Given
        Action<ExecSpec> action = spec -> {
            spec.commandLine("sh", "-c", "echo 'stdout'; echo 'stderr' >&2");
        };

        // When
        Provider<GradleExec.ExecResultWithOutput> resultProvider = gradleExec.lazyExec(action);
        GradleExec.ExecResultWithOutput result = resultProvider.get();

        // Then - verify that stdout, stderr, and result are all properly captured
        assertThat(result.stdOut().trim()).isEqualTo("stdout");
        assertThat(result.stdErr().trim()).isEqualTo("stderr");
        assertThat(result.result().getExitValue()).isEqualTo(0);
    }

    @Test
    void execWithFileOutput_shouldPreserveExactContent() throws IOException {
        // Given - test content with various whitespace and special characters
        String testContent = "  spaces  \n\ttabs\t\n\r\nwindows line endings\r\n  ";
        Action<ExecSpec> action = spec -> {
            spec.commandLine("sh", "-c", "printf '" + testContent + "'");
        };
        Path stdoutFile = tempDir.resolve("stdout.txt");
        Path stderrFile = tempDir.resolve("stderr.txt");

        // When
        gradleExec.execWithFileOutput(action, stdoutFile, stderrFile);

        // Then - exact content should be preserved
        String writtenContent = Files.readString(stdoutFile);
        assertThat(writtenContent).isEqualTo(testContent);
    }

    @Test
    void lazyExec_shouldCacheResultAndNotReExecuteCommand() throws InterruptedException {
        // Given - a command that outputs the current timestamp
        Action<ExecSpec> action = spec -> spec.commandLine("date", "+%s%N"); // Unix timestamp with nanoseconds

        // When
        Provider<GradleExec.ExecResultWithOutput> provider = gradleExec.lazyExec(action);
        GradleExec.ExecResultWithOutput result1 = provider.get();
        Thread.sleep(10);
        GradleExec.ExecResultWithOutput result2 = provider.get();

        // Then - both calls should return the exact same timestamp as should not re-run command
        assertThat(result1.stdOut()).isEqualTo(result2.stdOut());
        assertThat(result1.stdErr()).isEqualTo(result2.stdErr());
    }
}