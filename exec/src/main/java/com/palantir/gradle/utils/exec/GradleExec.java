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

import com.palantir.gradle.utils.providers.Zipper;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Nested;
import org.gradle.process.ExecOutput;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;

public abstract class GradleExec {

    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract ProviderFactory getProviderFactory();

    @Nested
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract Zipper getZip();

    /**
     * Executes a process using the provided {@link Action} to configure the {@link ExecSpec}.
     * <p>
     * Returns a {@link Provider} of {@link ExecResultWithOutput} wrapped in a {@link Result} type that allows
     * for flexible error handling. The Result can be unwrapped with {@code .get()} for default error
     * handling, or processed with custom error handling using {@code .mapFailure()}.
     * <p>
     * This method always captures stdout/stderr regardless of exit code, allowing callers to
     * provide context-specific error messages based on the actual output.
     *
     * @param action an action to configure the {@link ExecSpec} for the process to be executed
     * @return a Provider of {@link Result} containing the execution result with flexible error handling
     */
    public Provider<Result<ExecResultWithOutput>> exec(Action<? super ExecSpec> action) {
        // Capture the executable for error messages
        AtomicReference<String> executableHolder = new AtomicReference<>();

        Action<ExecSpec> wrappedAction = spec -> {
            action.execute(spec);
            executableHolder.set(spec.getExecutable());
            // Always ignore exit value to capture output
            spec.setIgnoreExitValue(true);
        };

        ExecOutput execOutput = getProviderFactory().exec(wrappedAction);

        Provider<String> stdoutProvider = execOutput.getStandardOutput().getAsText();
        Provider<String> stderrProvider = execOutput.getStandardError().getAsText();
        Provider<ExecResult> resultProvider = execOutput.getResult();

        return getZip().zip3(resultProvider, stdoutProvider, stderrProvider, (result, stdout, stderr) -> {
            ExecResultWithOutput output = ExecResultWithOutput.of(stdout, stderr, result);
            if (result.getExitValue() == 0) {
                return Result.success(output);
            } else {
                return Result.failure(output, executableHolder.get());
            }
        });
    }
}
