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

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

public final class DefaultFailableProvider<T> implements FailableProvider<T> {
    private final Provider<T> delegate;
    private final Predicate<T> isFailure;
    private final @Nullable Function<T, ? extends RuntimeException> exceptionMapper;

    public DefaultFailableProvider(
            Provider<T> delegate,
            Predicate<T> isFailure,
            @Nullable Function<T, ? extends RuntimeException> exceptionMapper) {
        this.delegate = delegate;
        this.isFailure = isFailure;
        this.exceptionMapper = exceptionMapper;
    }

    private void throwIfFailed(T value) {
        if (isFailure.test(value)) {
            if (exceptionMapper != null) {
                throw exceptionMapper.apply(value);
            } else {
                throw new RuntimeException("FailableProvider: value is considered a failure: " + value);
            }
        }
    }

    @Override
    public T get() {
        T value = delegate.get();
        throwIfFailed(value);
        return value;
    }

    @Override
    @Nullable
    public T getOrNull() {
        T value = delegate.getOrNull();
        if (value != null && isFailure.test(value)) {
            return null;
        }
        return value;
    }

    @Override
    public T getOrElse(T defaultValue) {
        T value = delegate.getOrElse(defaultValue);
        if (isFailure.test(value)) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public T getOrThrow(Function<T, ? extends RuntimeException> mapper) {
        T value = delegate.get();
        if (isFailure.test(value)) {
            throw mapper.apply(value);
        }
        return value;
    }

    @Override
    public FailableProvider<T> mapFailure(Function<T, ? extends RuntimeException> mapper) {
        return new DefaultFailableProvider<>(delegate, isFailure, mapper);
    }

    @Override
    public T getRaw() {
        return delegate.get();
    }

    @Override
    public <S> Provider<S> fold(Function<T, S> onSuccess, Function<T, S> onFailure) {
        return delegate.map(value -> {
            if (!isFailure.test(value)) {
                return onSuccess.apply(value);
            } else {
                return onFailure.apply(value);
            }
        });
    }

    @Override
    public boolean isPresent() {
        return delegate.isPresent();
    }

    @Override
    public Provider<T> orElse(T value) {
        return delegate.map(currentValue -> {
            if (isFailure.test(currentValue)) {
                return value;
            }
            return currentValue;
        });
    }

    @Override
    public Provider<T> orElse(Provider<? extends T> provider) {
        return delegate.flatMap(currentValue -> {
            if (isFailure.test(currentValue)) {
                return provider;
            }
            return delegate;
        });
    }

    @Override
    public Provider<T> forUseAtConfigurationTime() {
        return new DefaultFailableProvider<>(delegate.forUseAtConfigurationTime(), isFailure, exceptionMapper);
    }

    @Override
    public <S> Provider<S> map(Transformer<? extends S, ? super T> transformer) {
        return delegate.map(value -> {
            throwIfFailed(value);
            return transformer.transform(value);
        });
    }

    @Override
    public <S> Provider<S> flatMap(Transformer<? extends Provider<? extends S>, ? super T> transformer) {
        return delegate.flatMap(value -> {
            throwIfFailed(value);
            return transformer.transform(value);
        });
    }

    @Override
    public Provider<T> filter(Spec<? super T> spec) {
        Provider<T> filtered = delegate.filter(value -> {
            if (isFailure.test(value)) {
                return true; // Always pass through failures so they can be thrown later
            }
            return spec.isSatisfiedBy(value);
        });
        return new DefaultFailableProvider<>(filtered, isFailure, exceptionMapper);
    }

    @Override
    public <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super T, ? super U, ? extends R> combiner) {
        // Use the delegate directly for zip, but wrap the combiner to check for failures
        Provider<R> result = delegate.zip(
                right instanceof DefaultFailableProvider ? ((DefaultFailableProvider<U>) right).delegate : right,
                (leftValue, rightValue) -> {
                    throwIfFailed(leftValue);
                    return combiner.apply(leftValue, rightValue);
                });
        return result;
    }
}
