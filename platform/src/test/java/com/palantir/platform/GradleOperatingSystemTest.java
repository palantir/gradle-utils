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

package com.palantir.platform;

import static org.assertj.core.api.Assertions.assertThat;

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

public class GradleOperatingSystemTest {

    @Test
    public void canInstantiateAndCallGet() {
        Project project = ProjectBuilder.builder().build();
        ProviderFactory providerFactory = project.getProviders();

        GradleGradleOperatingSystem gradleOperatingSystem = new GradleGradleOperatingSystem() {
            @Override
            @Inject
            protected ProviderFactory getProviderFactory() {
                return providerFactory;
            }
        };

        OperatingSystem result = gradleOperatingSystem.get();

        assertThat(result)
                .as("OperatingSystem should not be null and should be of type OperatingSystem")
                .isNotNull()
                .isInstanceOf(OperatingSystem.class);
    }
}
