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

import com.palantir.gradle.utils.providers.FallibleProvider;
import com.palantir.gradle.utils.providers.Zipper;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Nested;
import org.gradle.process.ExecOutput;
import org.gradle.process.ExecSpec;

public abstract class GradleExec {

    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract ProviderFactory getProviderFactory();

    @Nested
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract Zipper getZip();

    /**
     * Executes a process and returns a FallibleProvider for the result.
     * <p>
     * Usage:
     * <pre>
     * def result = gradleExec.exec {
     *     commandLine 'git', 'status'
     * }
     *
     * // Handle success/failure
     * result.handle(
     *     { output -> println "Success: ${output.stdOut}" },
     *     { output -> println "Failed: ${output.stdErr}" }
     * )
     *
     * // Or throw on failure
     * def output = result.get().stdOut
     * </pre>
     */
    public FallibleProvider<GradleExecResult> exec(Action<? super ExecSpec> action) {
        AtomicReference<String> executable = new AtomicReference<>();

        Action<ExecSpec> captureAction = spec -> {
            action.execute(spec);
            executable.set(spec.getExecutable());
            spec.setIgnoreExitValue(true);
        };

        ExecOutput execOutput = getProviderFactory().exec(captureAction);

        Provider<GradleExecResult> resultProvider = getZip().zip3(
                        execOutput.getResult(),
                        execOutput.getStandardOutput().getAsText(),
                        execOutput.getStandardError().getAsText(),
                        (result, stdout, stderr) -> GradleExecResult.of(stdout, stderr, result));

        return FallibleProvider.of(
                resultProvider,
                result -> result.result().getExitValue() != 0,
                result -> new ExecFailedException(executable.get(), result));
    }
}
