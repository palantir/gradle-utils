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
import org.gradle.api.provider.Provider;

public final class ExecResultProviders {
    private ExecResultProviders() {}

    public static FailableProvider<ExecResultWithOutput> forExecResult(
            Provider<ExecResultWithOutput> delegate, String executable) {
        return new DefaultFailableProvider<>(
                delegate,
                result -> result.result().getExitValue() != 0,
                result -> new ExecFailedException(executable, result));
    }
}
