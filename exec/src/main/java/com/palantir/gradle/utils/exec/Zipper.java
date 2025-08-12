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

import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

public abstract class Zipper {

    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract ObjectFactory getObjectFactory();

    @SuppressWarnings("unchecked")
    public final <T1, T2, T3, R> Provider<R> zip(
            Provider<T1> provider1, Provider<T2> provider2, Provider<T3> provider3, Function3<T1, T2, T3, R> combiner) {
        ListProperty<Object> listProperty = getObjectFactory().listProperty(Object.class);
        listProperty.add(provider1);
        listProperty.add(provider2);
        listProperty.add(provider3);

        return listProperty.map(list -> {
            T1 arg1 = (T1) list.get(0);
            T2 arg2 = (T2) list.get(1);
            T3 arg3 = (T3) list.get(2);
            return combiner.apply(arg1, arg2, arg3);
        });
    }

    @FunctionalInterface
    public interface Function3<T1, T2, T3, R> {
        R apply(T1 arg1, T2 arg2, T3 arg3);
    }
}
