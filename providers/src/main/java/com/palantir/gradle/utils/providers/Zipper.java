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

import java.util.List;
import java.util.function.Function;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

/**
 * Utility for combining (zipping) multiple Gradle {@link Provider} instances into a single provider
 * whose value is computed from the values of the input providers.
 */
public abstract class Zipper {

    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract ObjectFactory getObjectFactory();

    /**
     * Combines a list of providers into a single provider whose value is computed by applying
     * the given combiner function to a list of the resolved provider values.
     *
     * @param providers the providers to combine
     * @param combiner a function that computes the result from the list of provider values
     * @param <R> the type of the combined provider's value
     * @return a provider that computes its value from the input providers
     */
    public final <R> Provider<R> zip(List<? extends Provider<?>> providers, Function<List<Object>, R> combiner) {
        ListProperty<Object> listProperty = getObjectFactory().listProperty(Object.class);
        providers.forEach(listProperty::add);
        return listProperty.map(combiner::apply);
    }

    /**
     * Combines three providers into a single provider whose value is computed by applying
     * the given combiner function to the resolved values of the input providers.
     *
     * @param provider1 the first provider
     * @param provider2 the second provider
     * @param provider3 the third provider
     * @param combiner a function that computes the result from the three provider values
     * @param <T1> the type of the first provider's value
     * @param <T2> the type of the second provider's value
     * @param <T3> the type of the third provider's value
     * @param <R> the type of the combined provider's value
     * @return a provider that computes its value from the three input providers
     */
    @SuppressWarnings("unchecked")
    public final <T1, T2, T3, R> Provider<R> zip(
            Provider<T1> provider1, Provider<T2> provider2, Provider<T3> provider3, Function3<T1, T2, T3, R> combiner) {
        return zip(
                List.of(provider1, provider2, provider3),
                list -> combiner.apply((T1) list.get(0), (T2) list.get(1), (T3) list.get(2)));
    }

    /**
     * Combines four providers into a single provider whose value is computed by applying
     * the given combiner function to the resolved values of the input providers.
     *
     * @param provider1 the first provider
     * @param provider2 the second provider
     * @param provider3 the third provider
     * @param provider4 the fourth provider
     * @param combiner a function that computes the result from the four provider values
     * @param <T1> the type of the first provider's value
     * @param <T2> the type of the second provider's value
     * @param <T3> the type of the third provider's value
     * @param <T4> the type of the fourth provider's value
     * @param <R> the type of the combined provider's value
     * @return a provider that computes its value from the four input providers
     */
    @SuppressWarnings("unchecked")
    public final <T1, T2, T3, T4, R> Provider<R> zip(
            Provider<T1> provider1,
            Provider<T2> provider2,
            Provider<T3> provider3,
            Provider<T4> provider4,
            Function4<T1, T2, T3, T4, R> combiner) {
        return zip(
                List.of(provider1, provider2, provider3, provider4),
                list -> combiner.apply((T1) list.get(0), (T2) list.get(1), (T3) list.get(2), (T4) list.get(3)));
    }

    @FunctionalInterface
    public interface Function3<T1, T2, T3, R> {
        R apply(T1 arg1, T2 arg2, T3 arg3);
    }

    @FunctionalInterface
    public interface Function4<T1, T2, T3, T4, R> {
        R apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4);
    }
}
