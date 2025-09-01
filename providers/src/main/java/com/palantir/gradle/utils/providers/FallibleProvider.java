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
 * A Provider that can represent operations that may fail, providing
 * type-safe error handling in Gradle's lazy configuration model.
 *
 * @param <T> The type of value this provider produces
 */
public interface FallibleProvider<T> extends Provider<T> {

    /**
     * Creates a FallibleProvider from a regular provider with a failure predicate.
     */
    static <T> FallibleProvider<T> of(
            Provider<T> delegate, Function<T, Boolean> isFailure, Function<T, ? extends RuntimeException> errorMapper) {
        return new DefaultFailableProvider<>(delegate, isFailure::apply, errorMapper);
    }

    /**
     * Gets the value, throwing a custom exception if failed.
     */
    T getOrThrow(Function<T, ? extends RuntimeException> exceptionMapper);

    /**
     * Returns a new FallibleProvider with a different exception mapper.
     */
    FallibleProvider<T> mapFailure(Function<T, ? extends RuntimeException> exceptionMapper);

    /**
     * Returns the raw value without throwing on failure.
     */
    T getRaw();

    /**
     * Transforms both success and failure cases.
     */
    <S> Provider<S> handle(Function<T, S> onSuccess, Function<T, S> onFailure);
}
