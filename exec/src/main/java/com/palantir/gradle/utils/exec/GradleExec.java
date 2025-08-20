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

import com.palantir.gradle.utils.providers.DefaultFailableProvider;
import com.palantir.gradle.utils.providers.FailableProvider;
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
     * Executes a process and returns a {@link FailableProvider} that captures stdout, stderr, and exit code.
     * <p>
     * The returned provider throws {@link ExecFailedException} on non-zero exit codes when {@code .get()}
     * is called, but allows custom error handling via {@code .mapFailure()} or {@code .fold()}.
     *
     * @param action configures the {@link ExecSpec} for the process to execute
     * @return a FailableProvider containing the execution result with captured output
     */
    public FailableProvider<ExecResultWithOutput> exec(Action<? super ExecSpec> action) {
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

        Provider<ExecResultWithOutput> combinedProvider = getZip().zip3(
                        resultProvider,
                        stdoutProvider,
                        stderrProvider,
                        (result, stdout, stderr) -> ExecResultWithOutput.of(stdout, stderr, result));

        return new DefaultFailableProvider<>(
                combinedProvider,
                result -> result.result().getExitValue() != 0,
                result -> new ExecFailedException(executableHolder.get(), result));
    }
}
