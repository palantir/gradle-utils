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

/**
 * Exception thrown when a process execution fails with a non-zero exit code.
 */
public class ExecFailedException extends RuntimeException {
    public ExecFailedException(String executable, ExecResultWithOutput execResult) {
        super(buildMessage(executable, execResult));
    }

    private static String buildMessage(String executable, ExecResultWithOutput execResult) {
        StringBuilder message = new StringBuilder();
        message.append("Process '")
                .append(executable != null ? executable : "<unknown>")
                .append("' failed with exit code: ")
                .append(execResult.result().getExitValue());

        String stdErr = execResult.stdErr().trim();
        String stdOut = execResult.stdOut().trim();

        if (!stdErr.isEmpty()) {
            message.append("\n\nStandard Error:\n").append(stdErr);
        }

        if (!stdOut.isEmpty()) {
            message.append("\n\nStandard Output:\n").append(stdOut);
        }

        return message.toString();
    }
}
