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

package com.palantir.gradle.utils.projectdependency;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.AdditionallyRunWithGradle;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@AdditionallyRunWithGradle(value = "8.10.2", reason = "Exercises the pre-8.11 compatibility path")
class ProjectDependencyUtilsIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject, SubProject subproject) {
        String projectVersion =
                Optional.ofNullable(System.getProperty("projectVersion")).orElseThrow();

        rootProject.buildGradle().prepend("""
            buildscript {
                repositories {
                    mavenLocal()
                }

                dependencies {
                    classpath 'com.palantir.gradle.utils:project-dependency-utils:%s'
                }
            }
            """, projectVersion);

        subproject.buildGradle().plugins().add("java-library");

        rootProject.buildGradle().append("""
            import com.palantir.gradle.utils.projectdependency.ProjectDependencyUtils
            import org.gradle.api.artifacts.ProjectDependency

            configurations {
                projectDeps
            }

            dependencies {
                projectDeps project(':subproject')
            }

            task printProjectPath {
                outputs.file('build/projectPath')
                doFirst {
                    ProjectDependency projectDependency = configurations.projectDeps.dependencies
                            .find { dependency -> dependency instanceof ProjectDependency }
                    outputs.files.singleFile << ProjectDependencyUtils.getProjectPath(projectDependency)
                }
            }
            """);
    }

    @Test
    void reads_the_path_of_a_project_dependency(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("printProjectPath").buildsSuccessfully();

        String projectPath = rootProject.buildDir().file("projectPath").text().strip();

        assertThat(projectPath).isEqualTo(":subproject");
    }
}
