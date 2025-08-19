/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.utils.exec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.gradle.utils.providers.FailableProvider;
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
    void beforeEach() {
        gradleExec = ProjectBuilder.builder().build().getObjects().newInstance(GradleExec.class);
    }

    @Test
    void should_return_provider() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("echo", "test");

        // When
        FailableProvider<ExecResultWithOutput> resultProvider = gradleExec.exec(action);

        // Then
        assertThat(resultProvider).isNotNull();
        assertThat(resultProvider).isInstanceOf(Provider.class);
        // Provider should not execute until .get() is called
        assertThat(resultProvider.isPresent()).isTrue();
    }

    @Test
    void should_defer_execution() throws IOException {
        // Given - a command that creates a file as a side effect
        Path sideEffectFile = tempDir.resolve("side-effect.txt");
        Action<ExecSpec> action = spec -> {
            spec.commandLine("sh", "-c", "echo 'executed' > " + sideEffectFile);
        };

        // When - create the provider
        FailableProvider<ExecResultWithOutput> resultProvider = gradleExec.exec(action);

        // Then - side effect should not have occurred yet
        assertThat(Files.exists(sideEffectFile)).isFalse();

        // When - actually get the result
        resultProvider.get();

        // Then - now the side effect should have occurred
        assertThat(Files.exists(sideEffectFile)).isTrue();
        assertThat(Files.readString(sideEffectFile).trim()).isEqualTo("executed");
    }

    @Test
    void should_chain_providers_correctly() {
        // Given
        Action<ExecSpec> action = spec -> {
            spec.commandLine("sh", "-c", "echo 'stdout'; echo 'stderr' >&2");
        };

        // When
        FailableProvider<ExecResultWithOutput> resultProvider = gradleExec.exec(action);
        ExecResultWithOutput execResult = resultProvider.get();

        // Then - verify that stdout, stderr, and result are all properly captured
        assertThat(execResult.stdOut().trim()).isEqualTo("stdout");
        assertThat(execResult.stdErr().trim()).isEqualTo("stderr");
        assertThat(execResult.result().getExitValue()).isEqualTo(0);
    }

    @Test
    void should_cache_result_and_not_re_execute_command() throws InterruptedException {
        // Given - a command that outputs the current timestamp
        Action<ExecSpec> action = spec -> spec.commandLine("date", "+%s%N"); // Unix timestamp with nanoseconds

        // When
        FailableProvider<ExecResultWithOutput> provider = gradleExec.exec(action);
        ExecResultWithOutput result1 = provider.get();
        Thread.sleep(10);
        ExecResultWithOutput result2 = provider.get();

        // Then - both calls should return the exact same timestamp as should not re-run command
        assertThat(result1.stdOut()).isEqualTo(result2.stdOut());
        assertThat(result1.stdErr()).isEqualTo(result2.stdErr());
    }

    @Test
    void should_return_success_for_zero_exit_code() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("echo", "hello");

        // When
        FailableProvider<ExecResultWithOutput> result = gradleExec.exec(action);

        // Then
        assertThat(result.get().stdOut().trim()).isEqualTo("hello");
        assertThat(result.get().result().getExitValue()).isEqualTo(0);
    }

    @Test
    void should_return_failure_for_non_zero_exit_code() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("sh", "-c", "exit 42");

        // When
        FailableProvider<ExecResultWithOutput> result = gradleExec.exec(action);

        // Then
        assertThat(result.getRaw().result().getExitValue()).isEqualTo(42);
    }

    @Test
    void should_throw_default_exception_on_failure_get() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("sh", "-c", "echo 'error' >&2; exit 1");

        // When
        FailableProvider<ExecResultWithOutput> result = gradleExec.exec(action);

        // Then
        assertThatThrownBy(result::get)
                .isInstanceOf(ExecFailedException.class)
                .hasMessageContaining("Process 'sh' failed with exit code: 1")
                .hasMessageContaining("error");
    }

    @Test
    void should_throw_custom_exception_with_getOrThrow() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("sh", "-c", "echo 'custom error' >&2; exit 1");

        // When
        FailableProvider<ExecResultWithOutput> result = gradleExec.exec(action);

        // Then
        assertThatThrownBy(() -> result.getOrThrow(output ->
                        new IllegalStateException("Custom: " + output.stdErr().trim())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Custom: custom error");
    }

    @Test
    void should_map_failure_with_custom_exception() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("sh", "-c", "echo 'not found' >&2; exit 127");

        // When
        FailableProvider<ExecResultWithOutput> result = gradleExec.exec(action).mapFailure(output -> {
            if (output.result().getExitValue() == 127) {
                return new IllegalStateException(
                        "Command not found: " + output.stdErr().trim());
            }
            return new RuntimeException("Unknown error");
        });

        // Then
        assertThatThrownBy(result::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Command not found: not found");
    }

    @Test
    void should_not_affect_success_when_mapping_failure() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("echo", "success");

        // When
        FailableProvider<ExecResultWithOutput> result =
                gradleExec.exec(action).mapFailure(_output -> new IllegalStateException("This should not be thrown"));

        // Then
        assertThat(result.get().stdOut().trim()).isEqualTo("success");
    }

    @Test
    void should_access_raw_result_without_throwing() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("sh", "-c", "echo 'output'; exit 1");

        // When
        FailableProvider<ExecResultWithOutput> result = gradleExec.exec(action);

        // Then - getRaw() should not throw even for failures
        ExecResultWithOutput raw = result.getRaw();
        assertThat(raw.stdOut().trim()).isEqualTo("output");
        assertThat(raw.result().getExitValue()).isEqualTo(1);
    }

    @Test
    void should_work_with_provider_map_for_success_case() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("echo", "test output");

        // When
        Provider<String> outputProvider =
                gradleExec.exec(action).map(result -> result.stdOut().trim());

        // Then
        assertThat(outputProvider.get()).isEqualTo("test output");
    }

    @Test
    void should_work_with_provider_map_for_failure_with_custom_error() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("sh", "-c", "echo 'git: not found' >&2; exit 128");

        // When
        Provider<String> outputProvider = gradleExec
                .exec(action)
                .mapFailure(output -> {
                    if (output.stdErr().contains("git: not found")) {
                        return new IllegalStateException("Git is not installed");
                    }
                    return new RuntimeException("Unknown git error");
                })
                .map(result -> result.stdOut().trim());

        // Then
        assertThatThrownBy(outputProvider::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Git is not installed");
    }

    @Test
    void should_handle_conditional_logic_with_fold() {
        // Given
        Action<ExecSpec> successAction = spec -> spec.commandLine("echo", "success");
        Action<ExecSpec> failureAction = spec -> spec.commandLine("sh", "-c", "exit 1");

        // When
        Provider<String> successProvider = gradleExec
                .exec(successAction)
                .fold(
                        success -> "Command succeeded: " + success.stdOut().trim(),
                        failure ->
                                "Command failed with code: " + failure.result().getExitValue());

        Provider<String> failureProvider = gradleExec
                .exec(failureAction)
                .fold(
                        success -> "Command succeeded: " + success.stdOut().trim(),
                        failure ->
                                "Command failed with code: " + failure.result().getExitValue());

        // Then
        assertThat(successProvider.get()).isEqualTo("Command succeeded: success");
        assertThat(failureProvider.get()).isEqualTo("Command failed with code: 1");
    }

    @Test
    void should_capture_both_stdout_and_stderr_on_failure() {
        // Given
        Action<ExecSpec> action = spec -> {
            spec.commandLine("sh", "-c", "echo 'stdout message'; echo 'stderr message' >&2; exit 1");
        };

        // When
        FailableProvider<ExecResultWithOutput> result = gradleExec.exec(action);

        // Then
        ExecResultWithOutput raw = result.getRaw();
        assertThat(raw.stdOut().trim()).isEqualTo("stdout message");
        assertThat(raw.stdErr().trim()).isEqualTo("stderr message");
        assertThat(raw.result().getExitValue()).isEqualTo(1);
    }

    @Test
    void should_chain_multiple_mapFailure_calls() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("sh", "-c", "exit 2");

        // When
        FailableProvider<ExecResultWithOutput> result = gradleExec
                .exec(action)
                .mapFailure(_output -> new RuntimeException("First mapping"))
                .mapFailure(_output -> new IllegalStateException("Second mapping"));

        // Then - the last mapFailure should win
        assertThatThrownBy(result::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Second mapping");
    }

    @Test
    void should_propagate_failure_through_map() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("sh", "-c", "exit 1");

        // When
        Provider<String> result =
                gradleExec.exec(action).map(output -> output.stdOut().trim());

        // Then
        assertThatThrownBy(result::get)
                .isInstanceOf(ExecFailedException.class)
                .hasMessageContaining("Process 'sh' failed with exit code: 1");
    }

    @Test
    void should_filter_successful_results() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("echo", "test");

        // When - filter that passes
        Provider<ExecResultWithOutput> filtered =
                gradleExec.exec(action).filter(result -> result.stdOut().contains("test"));

        // Then
        assertThat(filtered.isPresent()).isTrue();
        assertThat(filtered.get().stdOut().trim()).isEqualTo("test");

        // When - filter that fails
        Provider<ExecResultWithOutput> filteredOut =
                gradleExec.exec(action).filter(result -> result.stdOut().contains("notfound"));

        // Then
        assertThat(filteredOut.getOrNull()).isNull();
    }

    @Test
    void should_always_pass_failures_through_filter() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("sh", "-c", "exit 1");

        // When - even with a filter that would reject, failures pass through
        FailableProvider<ExecResultWithOutput> filtered = (FailableProvider<ExecResultWithOutput>)
                gradleExec.exec(action).filter(_result -> false); // This would reject everything

        // Then - failure still propagates
        assertThatThrownBy(filtered::get).isInstanceOf(ExecFailedException.class);
    }

    @Test
    void should_zip_with_other_provider() {
        // Given
        Action<ExecSpec> action = spec -> spec.commandLine("echo", "hello");
        Provider<String> other = gradleExec
                .exec(spec -> spec.commandLine("echo", "world"))
                .map(r -> r.stdOut().trim());

        // When
        Provider<String> combined = gradleExec
                .exec(action)
                .zip(other, (result, otherValue) -> result.stdOut().trim() + " " + otherValue);

        // Then
        assertThat(combined.get()).isEqualTo("hello world");
    }

    @Test
    void should_fail_fast_in_zip_when_execution_failed() {
        // Given
        Action<ExecSpec> failAction = spec -> spec.commandLine("sh", "-c", "exit 1");
        Provider<String> other = ProjectBuilder.builder().build().provider(() -> "other");

        // When
        Provider<String> combined =
                gradleExec.exec(failAction).zip(other, (_result, _otherValue) -> "should not reach here");

        // Then
        assertThatThrownBy(combined::get).isInstanceOf(ExecFailedException.class);
    }

    @Test
    void should_use_getOrElse_for_failures() {
        // Given
        Action<ExecSpec> successAction = spec -> spec.commandLine("echo", "success");
        Action<ExecSpec> failAction = spec -> spec.commandLine("sh", "-c", "exit 1");

        // When getting a successful result
        ExecResultWithOutput defaultValue = gradleExec.exec(successAction).get(); // Reuse the success result as default

        // When
        ExecResultWithOutput result = gradleExec.exec(failAction).getOrElse(defaultValue);

        // Then
        assertThat(result.stdOut().trim()).isEqualTo("success");
    }

    @Test
    void should_return_null_with_getOrNull_for_failures() {
        // Given
        Action<ExecSpec> failAction = spec -> spec.commandLine("sh", "-c", "exit 1");

        // When
        ExecResultWithOutput result = gradleExec.exec(failAction).getOrNull();

        // Then
        assertThat(result).isNull();
    }
}
