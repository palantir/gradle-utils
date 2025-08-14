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
     */
    private <R> Provider<R> zipList(List<? extends Provider<?>> providers, Function<List<Object>, R> combiner) {
        ListProperty<Object> listProperty = getObjectFactory().listProperty(Object.class);
        providers.forEach(listProperty::add);
        return listProperty.map(combiner::apply);
    }

    /**
     * Combines three providers into a single provider whose value is computed by applying
     * the given combiner function to the resolved values of the input providers.
     */
    @SuppressWarnings("unchecked")
    public final <T1, T2, T3, R> Provider<R> zip3(
            Provider<T1> provider1, Provider<T2> provider2, Provider<T3> provider3, Function3<T1, T2, T3, R> combiner) {
        return zipList(
                List.of(provider1, provider2, provider3),
                list -> combiner.apply((T1) list.get(0), (T2) list.get(1), (T3) list.get(2)));
    }

    /**
     * Combines four providers into a single provider whose value is computed by applying
     * the given combiner function to the resolved values of the input providers.
     */
    @SuppressWarnings("unchecked")
    public final <T1, T2, T3, T4, R> Provider<R> zip4(
            Provider<T1> provider1,
            Provider<T2> provider2,
            Provider<T3> provider3,
            Provider<T4> provider4,
            Function4<T1, T2, T3, T4, R> combiner) {
        return zipList(
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
