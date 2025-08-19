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

import java.util.function.Function;
import org.gradle.api.provider.Provider;

/**
 * A Provider that can handle execution failures gracefully.
 * Similar to Gradle's SetProperty, this extends Provider to provide lazy evaluation
 * while adding failure-aware operations.
 */
public interface ExecResultProvider extends Provider<ExecResultWithOutput> {

    /**
     * Gets the value, throwing a custom exception if the execution failed.
     */
    ExecResultWithOutput getOrThrow(Function<ExecResultWithOutput, ? extends RuntimeException> exceptionMapper);

    /**
     * Maps a failure to a different exception while preserving success values.
     * Returns a new ExecResultProvider with the mapped failure.
     */
    ExecResultProvider mapFailure(Function<ExecResultWithOutput, ? extends RuntimeException> exceptionMapper);

    /**
     * Returns true if the execution succeeded (exit code 0).
     */
    boolean isSuccess();

    /**
     * Returns the raw result without throwing, useful for custom handling.
     * This is similar to getOrNull() but always returns a value.
     */
    ExecResultWithOutput getRaw();

    /**
     * Maps the successful result to another type.
     * If the execution failed, the failure is propagated.
     */
    <S> Provider<S> mapSuccess(Function<ExecResultWithOutput, S> mapper);

    /**
     * Handles both success and failure cases, returning a regular Provider.
     * This is useful for converting back to standard Gradle providers.
     * Never throws an exception.
     */
    <S> Provider<S> fold(
            Function<ExecResultWithOutput, S> onSuccess,
            Function<ExecResultWithOutput, S> onFailure
    );
}