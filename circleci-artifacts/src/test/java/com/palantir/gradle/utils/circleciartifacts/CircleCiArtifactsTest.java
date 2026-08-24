/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.utils.circleciartifacts;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.execution.Options;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class CircleCiArtifactsTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        String projectVersion =
                Optional.ofNullable(System.getProperty("projectVersion")).orElseThrow();

        rootProject.buildGradle().prepend("""
            buildscript {
                repositories {
                    mavenLocal()
                }
                dependencies {
                    classpath 'com.palantir.gradle.utils:circleci-artifacts:%s'
                }
            }

            import com.palantir.gradle.utils.circleciartifacts.CircleCiArtifacts

            public abstract class CircleCiArtifactsTask extends DefaultTask {
              @Nested
              abstract CircleCiArtifacts getCircleCiArtifacts();
            }

            tasks.register("printCircleCiLocation", CircleCiArtifactsTask) {
                doLast { task ->
                    def artifactLocation = task.circleCiArtifacts.resolveArtifactLocation('location/in/artifacts')
                    if (artifactLocation.isPresent()) {
                        println "Physical path: ${artifactLocation.get().physicalPath()}"
                        println "External location: ${artifactLocation.get().externalLocation()}"
                        println "Circle link: ${artifactLocation.get().circleLink()}"
                    } else {
                        println "Not in Circle, empty artifact location"
                    }
                }
            }
            """, projectVersion);
    }

    @Test
    void can_use_circle_ci_artifacts_when_the_right_environment_variables_are_set(
            GradleInvoker gradle, RootProject rootProject) {
        Path fakeCircleArtifacts =
                rootProject.directory("build/circle-artifacts").path();
        String relativePath = rootProject.path().relativize(fakeCircleArtifacts).toString();

        InvocationResult result = gradle.with(Options.builder()
                        .addArgs("printCircleCiLocation")
                        .testingEnvironmentVariables(Map.of(
                                "CI", "true",
                                "CIRCLE_ARTIFACTS", relativePath,
                                "CIRCLE_PROJECT_USERNAME", "palantir",
                                "CIRCLE_PROJECT_REPONAME", "gradle-utils",
                                "CIRCLE_BUILD_NUM", "1234",
                                "CIRCLE_NODE_INDEX", "2345",
                                "CIRCLE_BUILD_URL", "https://circleci.com/gh/palantir/gradle-utils/1234",
                                "CIRCLE_WORKFLOW_JOB_ID", "abc-123-def-456"))
                        .build())
                .buildsSuccessfully();

        assertThat(result)
                .output()
                .containsPattern("Physical path: .*/build/circle-artifacts/location/in/artifacts")
                .containsPattern(
                        "External location: palantir/gradle-utils/1234/artifacts/2345/.*/location/in/artifacts")
                .containsPattern("Circle link:"
                        + " https://circleci.com/output/job/abc-123-def-456/artifacts/2345/.*/location/in/artifacts");
    }

    @Test
    void empty_property_if_were_not_in_circle(GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("printCircleCiLocation").buildsSuccessfully();

        assertThat(result).output().contains("Not in Circle, empty artifact location");
    }

    @Test
    void handles_missing_circle_ci_url_gracefully(GradleInvoker gradle, RootProject rootProject) {
        Path fakeCircleArtifacts = rootProject
                .directory("build/circle-artifacts")
                .createDirectories()
                .path()
                .toAbsolutePath();
        Path projectDir = rootProject.path().toAbsolutePath();
        String relativePath = projectDir.relativize(fakeCircleArtifacts).toString();

        InvocationResult result = gradle.with(Options.builder()
                        .addArgs("printCircleCiLocation")
                        .testingEnvironmentVariables(Map.of(
                                "CI", "true",
                                "CIRCLE_ARTIFACTS", relativePath,
                                "CIRCLE_PROJECT_USERNAME", "palantir",
                                "CIRCLE_PROJECT_REPONAME", "gradle-utils",
                                "CIRCLE_BUILD_NUM", "1234",
                                "CIRCLE_NODE_INDEX", "2345",
                                "CIRCLE_WORKFLOW_JOB_ID", "abc-123-def-456"))
                        .build())
                .buildsSuccessfully();

        assertThat(result)
                .output()
                .containsPattern("Physical path: .*/build/circle-artifacts/location/in/artifacts")
                .containsPattern(
                        "External location: palantir/gradle-utils/1234/artifacts/2345/.*/location/in/artifacts")
                .containsPattern("Circle link:"
                        + " https://<circle_url>/output/job/abc-123-def-456/artifacts/2345/.*/location/in/artifacts");
    }

    @Test
    void handles_custom_circle_home_directory_environment_variable(GradleInvoker gradle) {
        String customHome = "/custom/home/path/";

        InvocationResult result = gradle.with(Options.builder()
                        .addArgs("printCircleCiLocation")
                        .testingEnvironmentVariables(Map.of(
                                "CI", "true",
                                "CIRCLE_ARTIFACTS", customHome + "circle-artifacts",
                                "CIRCLE_PROJECT_USERNAME", "palantir",
                                "CIRCLE_PROJECT_REPONAME", "gradle-utils",
                                "CIRCLE_BUILD_NUM", "1234",
                                "CIRCLE_NODE_INDEX", "2345",
                                "CIRCLE_BUILD_URL", "https://circleci.com/gh/palantir/gradle-utils/1234",
                                "CIRCLE_WORKFLOW_JOB_ID", "abc-123-def-456",
                                "CIRCLE_HOME_DIRECTORY", customHome))
                        .build())
                .buildsSuccessfully();

        assertThat(result)
                .output()
                .contains("Physical path: " + customHome + "circle-artifacts/location/in/artifacts")
                .contains("External location:"
                        + " palantir/gradle-utils/1234/artifacts/2345/~/circle-artifacts/location/in/artifacts")
                .contains(
                        "Circle link:"
                            + " https://circleci.com/output/job/abc-123-def-456/artifacts/2345/~/circle-artifacts/location/in/artifacts");
    }
}
