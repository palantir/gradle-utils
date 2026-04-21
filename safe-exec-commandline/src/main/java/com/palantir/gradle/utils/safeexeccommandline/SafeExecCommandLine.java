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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class SafeExecCommandLine {

    private SafeExecCommandLine() {}

    /**
     * Workaround for <a href="https://github.com/gradle/gradle/issues/10483">gradle/gradle#10483</a>. On macOS, if the
     * first element of {@code commandLine} is a bare executable name (not a relative or absolute path), resolve it
     * against the {@code PATH} environment variable and return a new list with the first element replaced by the
     * resolved absolute path. If resolution fails, the host is not macOS, or {@code commandLine} is empty, the
     * original list is returned unchanged.
     */
    public static List<String> resolve(List<String> commandLine) {
        return resolve(commandLine, System.getProperty("os.name"), System.getenv("PATH"));
    }

    @VisibleForTesting
    static List<String> resolve(List<String> commandLine, @Nullable String osName, @Nullable String pathEnv) {
        if (osName == null || !osName.toLowerCase(Locale.ROOT).startsWith("mac")) {
            return commandLine;
        }

        if (commandLine.isEmpty()) {
            return commandLine;
        }

        String command = commandLine.get(0);
        if (command.startsWith("./") || command.startsWith("../") || command.startsWith("/")) {
            return commandLine;
        }

        if (pathEnv == null || pathEnv.isEmpty()) {
            return commandLine;
        }

        return findOnPath(command, pathEnv)
                .map(path -> Stream.concat(
                                Stream.of(path.toAbsolutePath().toString()),
                                commandLine.subList(1, commandLine.size()).stream())
                        .collect(Collectors.toUnmodifiableList()))
                .orElse(commandLine);
    }

    @SuppressWarnings("StreamFlatMapOptional")
    private static Optional<Path> findOnPath(String command, String pathEnv) {
        return Stream.of(pathEnv.split(":"))
                .map(Paths::get)
                .filter(Files::isDirectory)
                .map(dir -> findInDirectory(dir, command))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<Path> findInDirectory(Path directory, String command) {
        try (Stream<Path> contents = Files.list(directory)) {
            return contents.filter(
                            executable -> executable.getFileName().toString().equals(command))
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
