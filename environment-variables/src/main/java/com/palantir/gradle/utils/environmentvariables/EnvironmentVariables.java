/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.utils.environmentvariables;

import javax.inject.Inject;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

public abstract class EnvironmentVariables {

    @Inject
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    protected abstract ProviderFactory getProviderFactory();

    public final Provider<Boolean> isCi() {
        return envVarOrFromTestingProperty("CI").map(_value -> true).orElse(false);
    }

    public final Provider<Boolean> isCircleNode0OrLocal() {
        return envVarOrFromTestingProperty("CIRCLE_NODE_INDEX")
                .map(index -> index.equals("0"))
                .orElse(true);
    }

    public final Provider<String> envVarOrFromTestingProperty(String envVar) {
        Provider<Boolean> testingProvider = getProviderFactory()
                .gradleProperty("__TESTING")
                .map(Boolean::parseBoolean)
                .orElse(false);

        return testingProvider.flatMap(isTesting ->
                isTesting ? testingProperty(envVar) : getProviderFactory().environmentVariable(envVar));
    }

    private Provider<String> testingProperty(String name) {
        return getProviderFactory().gradleProperty("__TESTING_" + name);
    }
}
