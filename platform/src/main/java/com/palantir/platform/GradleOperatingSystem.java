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

package com.palantir.platform;

import com.palantir.gradle.utils.exec.ExecResultWithOutput;
import com.palantir.gradle.utils.exec.GradleExec;
import java.util.Locale;
import javax.inject.Inject;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;

public abstract class GradleOperatingSystem {
    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract ProviderFactory getProviderFactory();

    @Nested
    protected abstract GradleExec getGradleExec();

    private final Provider<OperatingSystem> linuxOperatingSystem = linuxLibcFromLdd();

    /**
     * Because this is prefixed with get-, Gradle thinks this is a property.
     * If GradleOperatingSystem is @Nested into a Gradle task, Gradle will nag you to mark
     * the property GradleOperatingSystem.operatingSystem as @Input, @Output, or @Internal
     * Hence, we mark it as @Internal
     */
    @Internal
    public final Provider<OperatingSystem> getOperatingSystem() {
        Provider<String> osNameProvider = getProviderFactory().systemProperty("os.name");

        Provider<OperatingSystem> nonLinuxOsProvider = osNameProvider.map(osName -> {
            String lowerCaseOsName = osName.toLowerCase(Locale.ROOT);

            if (lowerCaseOsName.startsWith("mac")) {
                return OperatingSystem.MACOS;
            }

            if (lowerCaseOsName.startsWith("windows")) {
                return OperatingSystem.WINDOWS;
            }

            return null;
        });

        return nonLinuxOsProvider.orElse(linuxOperatingSystem).orElse(osNameProvider.map(osName -> {
            throw new UnsupportedOperationException("Cannot get platform for operating system " + osName);
        }));
    }

    private Provider<OperatingSystem> linuxLibcFromLdd() {
        // Using handle to deal with both success and failure cases
        // ldd --version exits with 0 for glibc and 1 for musl, both are valid
        return getGradleExec()
                .exec(spec -> {
                    spec.commandLine("ldd", "--version");
                })
                .handle(
                        // Success case (exit code 0) - typically glibc
                        this::parseLibcFromOutput,
                        // Failure case - could be musl (exit 1) or actual failure
                        failure -> {
                            int exitCode = failure.result().getExitValue();
                            if (exitCode == 1) {
                                // Exit code 1 might be musl, check the output
                                return parseLibcFromOutput(failure);
                            }
                            String output = failure.stdOut().trim() + "\n"
                                    + failure.stdErr().trim();
                            throw new RuntimeException(String.format(
                                    "Failed to run ldd - exited with exit code %d. Output: %s.", exitCode, output));
                        });
    }

    private OperatingSystem parseLibcFromOutput(ExecResultWithOutput execResult) {
        String lowercaseOutput =
                (execResult.stdOut().trim() + "\n" + execResult.stdErr().trim()).toLowerCase(Locale.ROOT);

        if (lowercaseOutput.contains("glibc") || lowercaseOutput.contains("gnu libc")) {
            return OperatingSystem.LINUX_GLIBC;
        }
        if (lowercaseOutput.contains("musl")) {
            return OperatingSystem.LINUX_MUSL;
        }

        // If we can't determine the libc type from the output
        throw new UnsupportedOperationException(
                "Cannot work out libc used by this OS. ldd output was: " + lowercaseOutput);
    }
}
