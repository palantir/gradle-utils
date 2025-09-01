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

import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.function.Function;
import java.util.function.Predicate;
import org.gradle.api.provider.Provider;

/**
 * A Provider that can represent operations that may fail, providing
 * type-safe error handling in Gradle's lazy configuration model.
 *
 * @param <T> The type of value this provider produces
 */
public interface FallibleProvider<T> extends Provider<T> {

    /**
     * Creates a FallibleProvider from a regular provider that always succeeds.
     * Use failOn() to add failure conditions.
     */
    static <T> FallibleProvider<T> of(Provider<T> delegate) {
        return new DefaultFallibleProvider<>(
                delegate, _value -> false, _value -> new SafeIllegalStateException("Unexpected failure"));
    }

    /**
     * Creates a FallibleProvider from a regular provider with a failure predicate.
     */
    static <T> FallibleProvider<T> of(
            Provider<T> delegate, Predicate<T> isFailure, Function<T, ? extends RuntimeException> errorMapper) {
        return new DefaultFallibleProvider<>(delegate, isFailure, errorMapper);
    }

    /**
     * Adds a failure condition to this provider.
     *
     * @param isFailure Predicate to check if the value represents a failure
     * @param errorMapper Function to create an exception from a failure value
     * @return A new FallibleProvider with the failure condition added
     */
    default FallibleProvider<T> failOn(Predicate<T> isFailure, Function<T, ? extends RuntimeException> errorMapper) {
        return FallibleProvider.of(this, isFailure, errorMapper);
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
