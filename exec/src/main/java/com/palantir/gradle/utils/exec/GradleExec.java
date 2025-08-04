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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.process.ExecOutput;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.immutables.value.Value;

public abstract class GradleExec {

    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract ProviderFactory getProviderFactory();

    public final Provider<ExecResultWithOutput> lazyExec(Action<? super ExecSpec> action) {
        ExecOutput execOutput = getProviderFactory().exec(action);

        Provider<String> stdoutProvider = execOutput.getStandardOutput().getAsText();
        Provider<String> stderrProvider = execOutput.getStandardError().getAsText();
        Provider<ExecResult> resultProvider = execOutput.getResult();

        return stdoutProvider
                .zip(
                        stderrProvider,
                        (stdout, stderr) ->
                                resultProvider.map(result -> ExecResultWithOutput.of(stdout, stderr, result)))
                .flatMap(provider -> provider);
    }

    public final ExecResultWithOutput exec(Action<? super ExecSpec> action) {
        return lazyExec(action).get();
    }

    public final void execWithFileOutput(Action<? super ExecSpec> action, Path stdoutFile, Path stdErrFile)
            throws IOException {
        ExecResultWithOutput result = exec(action);
        result.result().assertNormalExitValue();
        Files.writeString(stdoutFile, result.stdOut());
        Files.writeString(stdErrFile, result.stdErr());
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
