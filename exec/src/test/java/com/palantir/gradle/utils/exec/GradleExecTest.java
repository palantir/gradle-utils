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
        Provider<GradleExec.ExecResultWithOutput> resultProvider = gradleExec.exec(action);

        // Then
        assertThat(resultProvider).isNotNull();
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
        Provider<GradleExec.ExecResultWithOutput> resultProvider = gradleExec.exec(action);

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
        Provider<GradleExec.ExecResultWithOutput> resultProvider = gradleExec.exec(action);
        GradleExec.ExecResultWithOutput result = resultProvider.get();

        // Then - verify that stdout, stderr, and result are all properly captured
        assertThat(result.stdOut().trim()).isEqualTo("stdout");
        assertThat(result.stdErr().trim()).isEqualTo("stderr");
        assertThat(result.result().getExitValue()).isEqualTo(0);
    }

    @Test
    void should_cache_result_and_not_re_execute_command() throws InterruptedException {
        // Given - a command that outputs the current timestamp
        Action<ExecSpec> action = spec -> spec.commandLine("date", "+%s%N"); // Unix timestamp with nanoseconds

        // When
        Provider<GradleExec.ExecResultWithOutput> provider = gradleExec.exec(action);
        GradleExec.ExecResultWithOutput result1 = provider.get();
        Thread.sleep(10);
        GradleExec.ExecResultWithOutput result2 = provider.get();

        // Then - both calls should return the exact same timestamp as should not re-run command
        assertThat(result1.stdOut()).isEqualTo(result2.stdOut());
        assertThat(result1.stdErr()).isEqualTo(result2.stdErr());
    }
}
