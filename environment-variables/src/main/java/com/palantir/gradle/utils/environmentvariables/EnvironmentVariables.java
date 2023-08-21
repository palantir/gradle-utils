/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.utils.environmentvariables;

import javax.inject.Inject;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

public abstract class EnvironmentVariables {

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    public final Provider<Boolean> isCi() {
        return envVarOrFromTestingProperty("CI").map(_value -> true).orElse(false);
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
