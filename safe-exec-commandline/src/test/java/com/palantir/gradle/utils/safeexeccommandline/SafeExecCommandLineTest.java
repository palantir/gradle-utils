/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.gradle.utils.safeexeccommandline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeExecCommandLineTest {

    private static final String LINUX = "Linux";
    private static final String WINDOWS = "Windows 11";
    private static final String MACOS = "Mac OS X";

    @TempDir
    Path tempDir;

    @Test
    void returns_input_unchanged_on_non_mac() throws IOException {
        Path pathDir = Files.createDirectory(tempDir.resolve("bin"));
        Files.createFile(pathDir.resolve("docker"));

        List<String> input = List.of("docker", "--version");

        assertThat(SafeExecCommandLine.resolve(input, LINUX, pathDir.toString()))
                .as("the workaround is macOS-only; Linux should be untouched even when the command exists on PATH")
                .isEqualTo(input);
        assertThat(SafeExecCommandLine.resolve(input, WINDOWS, pathDir.toString()))
                .as("the workaround is macOS-only; Windows should be untouched even when the command exists on PATH")
                .isEqualTo(input);
    }

    @Test
    void returns_input_unchanged_when_os_name_is_null() {
        List<String> input = List.of("docker", "--version");

        assertThat(SafeExecCommandLine.resolve(input, null, "/usr/local/bin"))
                .as("null os.name can't be proven to be macOS, so we must not modify the command")
                .isEqualTo(input);
    }

    @Test
    void returns_input_unchanged_when_empty() {
        assertThat(SafeExecCommandLine.resolve(List.of(), MACOS, "/usr/local/bin"))
                .isEmpty();
    }

    @Test
    void returns_input_unchanged_when_command_is_relative_path() throws IOException {
        Path pathDir = Files.createDirectory(tempDir.resolve("bin"));
        Files.createFile(pathDir.resolve("script"));

        assertThat(SafeExecCommandLine.resolve(List.of("./script", "arg"), MACOS, pathDir.toString()))
                .as("a './' relative path signals the caller already picked an executable, so PATH lookup must not"
                        + " silently rewrite it")
                .containsExactly("./script", "arg");
        assertThat(SafeExecCommandLine.resolve(List.of("../script", "arg"), MACOS, pathDir.toString()))
                .as("a '../' relative path signals the caller already picked an executable, so PATH lookup must not"
                        + " silently rewrite it")
                .containsExactly("../script", "arg");
    }

    @Test
    void returns_input_unchanged_when_command_is_absolute_path() throws IOException {
        Path pathDir = Files.createDirectory(tempDir.resolve("bin"));
        Files.createFile(pathDir.resolve("docker"));

        assertThat(SafeExecCommandLine.resolve(
                        List.of("/usr/local/bin/docker", "--version"), MACOS, pathDir.toString()))
                .as("an absolute path is already unambiguous; PATH lookup must not change it")
                .containsExactly("/usr/local/bin/docker", "--version");
    }

    @Test
    void resolves_command_to_absolute_path_on_mac_when_found_in_path() throws IOException {
        Path pathDir = Files.createDirectory(tempDir.resolve("bin"));
        Path dockerExecutable = Files.createFile(pathDir.resolve("docker"));

        List<String> resolved =
                SafeExecCommandLine.resolve(List.of("docker", "run", "--rm", "alpine"), MACOS, pathDir.toString());

        assertThat(resolved)
                .as("the core workaround: on macOS the bare 'docker' must be rewritten to its absolute path while the"
                        + " remaining arguments are preserved in order")
                .containsExactly(dockerExecutable.toAbsolutePath().toString(), "run", "--rm", "alpine");
    }

    @Test
    void returns_input_unchanged_on_mac_when_command_not_found_in_path() throws IOException {
        Path pathDir = Files.createDirectory(tempDir.resolve("bin"));
        Files.createFile(pathDir.resolve("something-else"));

        List<String> input = List.of("docker", "--version");

        assertThat(SafeExecCommandLine.resolve(input, MACOS, pathDir.toString()))
                .as("if we can't find the executable on PATH we fall through to the original command so Gradle's own"
                        + " lookup has a chance to run")
                .isEqualTo(input);
    }

    @Test
    void returns_input_unchanged_on_mac_when_path_env_is_null_or_empty() {
        List<String> input = List.of("docker", "--version");

        assertThat(SafeExecCommandLine.resolve(input, MACOS, null))
                .as("with no PATH we have nowhere to search, so the caller's command passes through unchanged")
                .isEqualTo(input);
        assertThat(SafeExecCommandLine.resolve(input, MACOS, ""))
                .as("with an empty PATH we have nowhere to search, so the caller's command passes through unchanged")
                .isEqualTo(input);
    }

    @Test
    void picks_first_matching_directory_when_path_contains_multiple_entries() throws IOException {
        Path firstDir = Files.createDirectory(tempDir.resolve("first"));
        Path secondDir = Files.createDirectory(tempDir.resolve("second"));
        Path firstDocker = Files.createFile(firstDir.resolve("docker"));
        Files.createFile(secondDir.resolve("docker"));

        String pathEnv = firstDir + ":" + secondDir;

        List<String> resolved = SafeExecCommandLine.resolve(List.of("docker"), MACOS, pathEnv);

        assertThat(resolved)
                .as("resolution must match the shell's PATH precedence: the earliest directory with a hit wins")
                .containsExactly(firstDocker.toAbsolutePath().toString());
    }

    @Test
    void ignores_non_existent_directories_in_path() throws IOException {
        Path realDir = Files.createDirectory(tempDir.resolve("real"));
        Path dockerExecutable = Files.createFile(realDir.resolve("docker"));

        String pathEnv = tempDir.resolve("does-not-exist") + ":" + realDir;

        List<String> resolved = SafeExecCommandLine.resolve(List.of("docker"), MACOS, pathEnv);

        assertThat(resolved)
                .as("real-world PATH entries often point to missing directories; those must be skipped silently rather"
                        + " than aborting the lookup")
                .containsExactly(dockerExecutable.toAbsolutePath().toString());
    }
}
