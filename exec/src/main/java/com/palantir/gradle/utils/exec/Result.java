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

/**
 * A Result type for process execution that allows flexible error handling.
 */
public abstract class Result<T> {

    /**
     * Gets the value, throwing the default exception if the execution failed.
     */
    public abstract T get();

    /**
     * Gets the value, throwing a custom exception if the execution failed.
     */
    public abstract T getOrThrow(Function<ExecResultWithOutput, ? extends RuntimeException> exceptionMapper);

    /**
     * Maps a failure to a different exception while preserving success values.
     */
    public abstract Result<T> mapFailure(Function<ExecResultWithOutput, ? extends RuntimeException> exceptionMapper);

    /**
     * Returns true if the execution succeeded (exit code 0).
     */
    public abstract boolean isSuccess();

    /**
     * Returns the raw result without throwing, useful for custom handling.
     */
    public abstract ExecResultWithOutput getRaw();

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(T value, String executable) {
        return new Failure<>(value, executable);
    }

    private static class Success<T> extends Result<T> {
        private final T value;

        Success(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public T getOrThrow(Function<ExecResultWithOutput, ? extends RuntimeException> _exceptionMapper) {
            return value;
        }

        @Override
        public Result<T> mapFailure(Function<ExecResultWithOutput, ? extends RuntimeException> _exceptionMapper) {
            return this;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public ExecResultWithOutput getRaw() {
            return (ExecResultWithOutput) value;
        }
    }

    private static class Failure<T> extends Result<T> {
        private final T value;
        private final String executable;
        private RuntimeException customException;

        Failure(T value, String executable) {
            this.value = value;
            this.executable = executable;
        }

        @Override
        public T get() {
            if (customException != null) {
                throw customException;
            }
            throw new ExecFailedException(executable, (ExecResultWithOutput) value);
        }

        @Override
        public T getOrThrow(Function<ExecResultWithOutput, ? extends RuntimeException> exceptionMapper) {
            throw exceptionMapper.apply((ExecResultWithOutput) value);
        }

        @Override
        public Result<T> mapFailure(Function<ExecResultWithOutput, ? extends RuntimeException> exceptionMapper) {
            Failure<T> newFailure = new Failure<>(value, executable);
            newFailure.customException = exceptionMapper.apply((ExecResultWithOutput) value);
            return newFailure;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public ExecResultWithOutput getRaw() {
            return (ExecResultWithOutput) value;
        }
    }
}
