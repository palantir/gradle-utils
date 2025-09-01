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

import com.palantir.logsafe.Preconditions;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

final class DefaultFailableProvider<T> implements FallibleProvider<T> {
    private final Provider<T> delegate;
    private final Predicate<T> isFailure;
    private final Function<T, ? extends RuntimeException> exceptionMapper;

    DefaultFailableProvider(
            Provider<T> delegate, Predicate<T> isFailure, Function<T, ? extends RuntimeException> exceptionMapper) {
        this.delegate = Preconditions.checkNotNull(delegate, "delegate");
        this.isFailure = Preconditions.checkNotNull(isFailure, "isFailure");
        this.exceptionMapper = Preconditions.checkNotNull(exceptionMapper, "exceptionMapper");
    }

    private void throwIfFailed(T value) {
        if (isFailure.test(value)) {
            throw exceptionMapper.apply(value);
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
    public FallibleProvider<T> mapFailure(Function<T, ? extends RuntimeException> mapper) {
        return new DefaultFailableProvider<>(delegate, isFailure, mapper);
    }

    @Override
    public T getRaw() {
        return delegate.get();
    }

    @Override
    public <S> Provider<S> handle(Function<T, S> onSuccess, Function<T, S> onFailure) {
        return delegate.map(value -> isFailure.test(value) ? onFailure.apply(value) : onSuccess.apply(value));
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
        Provider<T> filtered = delegate.filter(value -> isFailure.test(value) || spec.isSatisfiedBy(value));
        return new DefaultFailableProvider<>(filtered, isFailure, exceptionMapper);
    }

    @Override
    public Provider<T> orElse(T value) {
        return delegate.map(currentValue -> isFailure.test(currentValue) ? value : currentValue);
    }

    @Override
    public Provider<T> orElse(Provider<? extends T> provider) {
        return delegate.flatMap(currentValue -> isFailure.test(currentValue) ? provider : delegate);
    }

    @Override
    public Provider<T> forUseAtConfigurationTime() {
        return new DefaultFailableProvider<>(delegate.forUseAtConfigurationTime(), isFailure, exceptionMapper);
    }

    @Override
    public <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super T, ? super U, ? extends R> combiner) {
        if (right instanceof DefaultFailableProvider) {
            DefaultFailableProvider<U> rightFailable = (DefaultFailableProvider<U>) right;
            return delegate.zip(rightFailable.delegate, (leftValue, rightValue) -> {
                throwIfFailed(leftValue);
                rightFailable.throwIfFailed(rightValue);
                return combiner.apply(leftValue, rightValue);
            });
        } else {
            return delegate.zip(right, (leftValue, rightValue) -> {
                throwIfFailed(leftValue);
                return combiner.apply(leftValue, rightValue);
            });
        }
    }

    @Override
    public boolean isPresent() {
        return delegate.isPresent();
    }
}
