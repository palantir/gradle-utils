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

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class EnvironmentVariablesTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject
                .buildGradle()
                .prepend(
                        """
                        buildscript {
                            repositories {
                                mavenLocal()
                            }
                            dependencies {
                                classpath 'com.palantir.gradle.utils:environment-variables:%s'
                            }
                        }
                        """,
                        Optional.ofNullable(System.getProperty("projectVersion"))
                                .orElseThrow());

        rootProject.buildGradle().append("""
            import com.palantir.gradle.utils.environmentvariables.EnvironmentVariables

            public abstract class TestClass {
                @Nested
                abstract EnvironmentVariables getEnvironmentVariables()
            }

            def variables = objects.newInstance(TestClass).environmentVariables
            println('Variable: ' + variables.envVarOrFromTestingProperty('VARIABLE').getOrNull())
            println('isCircleNode0OrLocal: ' + variables.isCircleNode0OrLocal().getOrNull())
            println('isCi: ' + variables.isCi().getOrNull())
            """);
    }

    @Test
    void can_get_testing_variables(GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("help", "-P__TESTING=true", "-P__TESTING_VARIABLE=test")
                .buildsSuccessfully();

        assertThat(result).output().contains("Variable: test");
    }

    @Test
    void can_get_environment_variables(GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("help").buildsSuccessfully();

        assertThat(result).output().contains("Variable: actual value");
    }

    @Test
    void isCircleNode0OrLocal_returns_true_on_circle_node_0(GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("help", "-P__TESTING=true", "-P__TESTING_CIRCLE_NODE_INDEX=0")
                .buildsSuccessfully();

        assertThat(result).output().contains("isCircleNode0OrLocal: true");
    }

    @Test
    void isCircleNode0OrLocal_returns_false_on_circle_node_1(GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("help", "-P__TESTING=true", "-P__TESTING_CIRCLE_NODE_INDEX=1")
                .buildsSuccessfully();

        assertThat(result).output().contains("isCircleNode0OrLocal: false");
    }

    @Test
    void isCircleNode0OrLocal_returns_true_locally(GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("help", "-P__TESTING=true").buildsSuccessfully();

        assertThat(result).output().contains("isCircleNode0OrLocal: true");
    }

    @Test
    void isCi_returns_true_on_circle_node(GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("help", "-P__TESTING=true", "-P__TESTING_CI=true")
                .buildsSuccessfully();

        assertThat(result).output().contains("isCi: true");
    }

    @Test
    void isCi_returns_false_locally(GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("help", "-P__TESTING=true").buildsSuccessfully();

        assertThat(result).output().contains("isCi: false");
    }

    @Test
    void isCi_returns_false_if_CI_equals_false(GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("help", "-P__TESTING=true", "-P__TESTING_CI=false")
                .buildsSuccessfully();

        assertThat(result).output().contains("isCi: false");
    }
}
