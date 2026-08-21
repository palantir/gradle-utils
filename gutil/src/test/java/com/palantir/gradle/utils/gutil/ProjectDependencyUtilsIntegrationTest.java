/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.utils.gutil;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class ProjectDependencyUtilsIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject, SubProject subproject) {
        String projectVersion =
                Optional.ofNullable(System.getProperty("projectVersion")).orElseThrow();

        rootProject.buildGradle().prepend("""
            buildscript {
                repositories {
                    mavenCentral()
                    mavenLocal()
                }
                dependencies {
                    classpath 'com.palantir.gradle.utils:gutil:%s'
                }
            }
            """, projectVersion);

        rootProject.buildGradle().append("""
            import com.palantir.gradle.utils.gutil.ProjectDependencyUtils

            def dependency = dependencies.project(path: ':subproject')
            println('Dependency project: ' + ProjectDependencyUtils.getDependencyProject(project, dependency).path)
            """);
        subproject.buildGradle().plugins().add("java-library");
    }

    @Test
    void resolves_project_dependency(GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("help").buildsSuccessfully();

        assertThat(result).output().contains("Dependency project: :subproject");
    }
}
