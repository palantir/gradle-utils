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

import java.util.Locale;
import javax.inject.Inject;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.process.ExecOutput;

public abstract class GradleOperatingSystem {
    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract ProviderFactory getProviderFactory();

    public final Provider<OperatingSystem> getOperatingSystem() {
        Provider<String> osNameProvider = getProviderFactory().systemProperty("os.name");

        return osNameProvider.map(osName -> {
            String lowerCaseOsName = osName.toLowerCase(Locale.ROOT);

            if (lowerCaseOsName.startsWith("mac")) {
                return OperatingSystem.MACOS;
            }

            if (lowerCaseOsName.startsWith("windows")) {
                return OperatingSystem.WINDOWS;
            }

            if (lowerCaseOsName.startsWith("linux")) {
                return linuxLibcFromLdd();
            }

            throw new UnsupportedOperationException("Cannot get platform for operating system " + osName);
        });
    }

    private OperatingSystem linuxLibcFromLdd() {
        ExecOutput result = getProviderFactory().exec(spec -> {
            spec.commandLine("ldd", "--version");
            spec.setIgnoreExitValue(true);
        });

        String stdout = result.getStandardOutput().getAsText().get().trim();
        String stderr = result.getStandardError().getAsText().get().trim();
        String lowercaseOutput = (stdout + "\n" + stderr).toLowerCase(Locale.ROOT);

        if (lowercaseOutput.contains("glibc") || lowercaseOutput.contains("gnu libc")) {
            return OperatingSystem.LINUX_GLIBC;
        }

        if (lowercaseOutput.contains("musl")) {
            return OperatingSystem.LINUX_MUSL;
        }

        int exitValue = result.getResult().get().getExitValue();
        if (exitValue == 0 || exitValue == 1) {
            throw new UnsupportedOperationException(
                    "Cannot work out libc used by this OS. ldd output was: " + lowercaseOutput);
        }

        throw new RuntimeException(
                String.format("Failed to run ldd - exited with exit code %d. Output: %s.", exitValue, lowercaseOutput));
    }
}
