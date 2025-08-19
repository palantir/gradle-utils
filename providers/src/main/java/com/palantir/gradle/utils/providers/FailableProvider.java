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

package com.palantir.gradle.utils.providers;

import java.util.function.Function;
import org.gradle.api.provider.Provider;

/**
 * A Provider that can represent both success and failure, propagating failure-aware semantics
 * through mapping, folding, etc.
 */
public interface FailableProvider<T> extends Provider<T> {

    /**
     * Gets the value, throwing a custom exception if the value is a failure.
     */
    T getOrThrow(Function<T, ? extends RuntimeException> exceptionMapper);

    /**
     * Returns a new FailableProvider that uses the given exception mapper for failures.
     */
    FailableProvider<T> mapFailure(Function<T, ? extends RuntimeException> exceptionMapper);

    /**
     * Returns the raw value (maybe a failure) without throwing.
     */
    T getRaw();

    /**
     * Handles both success and failure cases, returning a regular Provider.
     * Never throws.
     */
    <S> Provider<S> fold(Function<T, S> onSuccess, Function<T, S> onFailure);

    // Provider<T> already has: get(), getOrNull(), getOrElse(), isPresent(), map, flatMap, filter, zip, etc.
}
