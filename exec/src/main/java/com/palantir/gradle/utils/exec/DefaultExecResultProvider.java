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

import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

/**
 * Alternative implementation that better preserves failure semantics through transformations.
 * This version treats failures as a special state that propagates through operations.
 */
class DefaultExecResultProvider implements ExecResultProvider {
    private final Provider<ExecResultWithOutput> delegate;
    private final String executable;
    private final Function<ExecResultWithOutput, ? extends RuntimeException> exceptionMapper;

    DefaultExecResultProvider(Provider<ExecResultWithOutput> delegate, String executable) {
        this(delegate, executable, null);
    }

    private DefaultExecResultProvider(
            Provider<ExecResultWithOutput> delegate,
            String executable,
            @Nullable Function<ExecResultWithOutput, ? extends RuntimeException> exceptionMapper) {
        this.delegate = delegate;
        this.executable = executable;
        this.exceptionMapper = exceptionMapper;
    }

    private void throwIfFailed(ExecResultWithOutput result) {
        if (result.result().getExitValue() != 0) {
            if (exceptionMapper != null) {
                throw exceptionMapper.apply(result);
            } else {
                throw new ExecFailedException(executable, result);
            }
        }
    }

    @Override
    public ExecResultWithOutput get() {
        ExecResultWithOutput result = delegate.get();
        throwIfFailed(result);
        return result;
    }

    @Override
    @Nullable
    public ExecResultWithOutput getOrNull() {
        ExecResultWithOutput result = delegate.getOrNull();
        if (result != null && result.result().getExitValue() != 0) {
            // Return null for failures to maintain Provider contract
            return null;
        }
        return result;
    }

    @Override
    public ExecResultWithOutput getOrElse(ExecResultWithOutput defaultValue) {
        ExecResultWithOutput result = delegate.get();
        if (result.result().getExitValue() != 0) {
            return defaultValue;
        }
        return result;
    }

    @Override
    public ExecResultWithOutput getOrThrow(Function<ExecResultWithOutput, ? extends RuntimeException> mapper) {
        ExecResultWithOutput result = delegate.get();
        if (result.result().getExitValue() != 0) {
            throw mapper.apply(result);
        }
        return result;
    }

    @Override
    public ExecResultProvider mapFailure(Function<ExecResultWithOutput, ? extends RuntimeException> mapper) {
        return new DefaultExecResultProvider(delegate, executable, mapper);
    }

    @Override
    public boolean isSuccess() {
        ExecResultWithOutput result = delegate.get();
        return result.result().getExitValue() == 0;
    }

    @Override
    public ExecResultWithOutput getRaw() {
        return delegate.get();
    }

    @Override
    public <S> Provider<S> mapSuccess(Function<ExecResultWithOutput, S> mapper) {
        return map(result -> {
            throwIfFailed(result);
            return mapper.apply(result);
        });
    }

    @Override
    public <S> Provider<S> fold(
            Function<ExecResultWithOutput, S> onSuccess, Function<ExecResultWithOutput, S> onFailure) {
        // fold should NOT throw - it handles both cases
        return delegate.map(result -> {
            if (result.result().getExitValue() == 0) {
                return onSuccess.apply(result);
            } else {
                return onFailure.apply(result);
            }
        });
    }

    @Override
    public boolean isPresent() {
        // A failed execution is still "present"
        return delegate.isPresent();
    }

    @Override
    public Provider<ExecResultWithOutput> orElse(ExecResultWithOutput value) {
        // This creates a new provider that returns the default value if the original is absent
        // but still propagates failures
        return new DefaultExecResultProvider(delegate.orElse(value), executable, exceptionMapper);
    }

    @Override
    public Provider<ExecResultWithOutput> orElse(Provider<? extends ExecResultWithOutput> provider) {
        // Similar to above, but with a provider
        return new DefaultExecResultProvider(delegate.orElse(provider), executable, exceptionMapper);
    }

    @Override
    public Provider<ExecResultWithOutput> forUseAtConfigurationTime() {
        return new DefaultExecResultProvider(delegate.forUseAtConfigurationTime(), executable, exceptionMapper);
    }

    @Override
    public <S> Provider<S> map(Transformer<? extends S, ? super ExecResultWithOutput> transformer) {
        // map should propagate the failure by throwing
        return delegate.map(result -> {
            throwIfFailed(result);
            return transformer.transform(result);
        });
    }

    @Override
    public <S> Provider<S> flatMap(
            Transformer<? extends Provider<? extends S>, ? super ExecResultWithOutput> transformer) {
        // flatMap should also propagate failures
        return delegate.flatMap(result -> {
            throwIfFailed(result);
            return transformer.transform(result);
        });
    }

    @Override
    public Provider<ExecResultWithOutput> filter(Spec<? super ExecResultWithOutput> spec) {
        // For filter, we have a choice:
        // Option 1: Failures always pass through (shown here)
        // Option 2: Failures are filtered out (would return empty provider)
        Provider<ExecResultWithOutput> filtered = delegate.filter(result -> {
            // Failures always pass the filter (so they can be thrown later)
            if (result.result().getExitValue() != 0) {
                return true;
            }
            // For success, apply the spec
            return spec.isSatisfiedBy(result);
        });
        return new DefaultExecResultProvider(filtered, executable, exceptionMapper);
    }

    @Override
    public <U, R> Provider<R> zip(
            Provider<U> right, BiFunction<? super ExecResultWithOutput, ? super U, ? extends R> combiner) {
        // zip should fail fast if the execution failed
        return delegate.zip(right, (execResult, rightValue) -> {
            throwIfFailed(execResult);
            return combiner.apply(execResult, rightValue);
        });
    }
}
