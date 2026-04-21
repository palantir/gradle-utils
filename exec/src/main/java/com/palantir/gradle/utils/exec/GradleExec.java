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
import com.palantir.gradle.utils.safeexeccommandline.SafeExecCommandLine;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Nested;
import org.gradle.process.ExecOutput;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.immutables.value.Value;

public abstract class GradleExec {

    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract ProviderFactory getProviderFactory();

    @Nested
    protected abstract Zipper getZip();

    /**
     * Executes a process using the provided {@link Action} to configure the {@link ExecSpec}.
     * <p>
     * This method always sets {@code ignoreExitValue} to {@code true} on the {@code ExecSpec},
     * ensuring that the build does not fail regardless of the process exit code. Callers do need
     * to handle exit codes manually.
     * <p>
     * The result includes the process's standard output, standard error, and the {@link ExecResult}.
     *
     * @param action an action to configure the {@link ExecSpec} for the process to be executed
     * @return a Provider of {@link ExecResultWithOutput} containing the standard output, standard error,
     *         and execution result
     */
    public Provider<ExecResultWithOutput> exec(Action<? super ExecSpec> action) {
        Action<ExecSpec> wrappedAction = spec -> {
            action.execute(spec);
            spec.setCommandLine(SafeExecCommandLine.resolve(spec.getCommandLine()));
            spec.setIgnoreExitValue(true);
        };

        ExecOutput execOutput = getProviderFactory().exec(wrappedAction);

        Provider<String> stdoutProvider = execOutput.getStandardOutput().getAsText();
        Provider<String> stderrProvider = execOutput.getStandardError().getAsText();
        Provider<ExecResult> resultProvider = execOutput.getResult();

        return getZip().zip3(
                        resultProvider,
                        stdoutProvider,
                        stderrProvider,
                        (result, stdout, stderr) -> ExecResultWithOutput.of(stdout, stderr, result));
    }

    @Value.Immutable
    public interface ExecResultWithOutput {
        String stdOut();

        String stdErr();

        ExecResult result();

        static ExecResultWithOutput of(String stdOut, String stdErr, ExecResult result) {
            return ImmutableExecResultWithOutput.builder()
                    .stdOut(stdOut)
                    .stdErr(stdErr)
                    .result(result)
                    .build();
        }
    }
}
