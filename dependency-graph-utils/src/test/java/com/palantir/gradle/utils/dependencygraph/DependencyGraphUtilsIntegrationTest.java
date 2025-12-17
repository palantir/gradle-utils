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

package com.palantir.gradle.utils.dependencygraph;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class DependencyGraphUtilsIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject, SubProject subproject) {
        rootProject.buildGradle().append("""
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }

                dependencies {
                    classpath 'com.palantir.gradle.consistentversions:gradle-consistent-versions:2.16.0'
                }
            }

            allprojects {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }
            }
            """);

        rootProject.buildGradle().plugins().add("java-library");
        subproject.buildGradle().plugins().add("java-library");

        subproject.buildGradle().append("""
            import com.palantir.gradle.utils.dependencygraph.DependencyGraphUtils

            task printAllDeps {
                inputs.property('configurationName', 'runtimeClasspath')
                outputs.file('build/allDeps')

                doFirst {
                    def root = project.configurations.getByName(inputs.properties.get('configurationName'))
                            .incoming.resolutionResult.rootComponent.get()
                    def all = DependencyGraphUtils.allComponentResultsFromRoot(root)
                    outputs.files.singleFile << all.collect { it.toString() }.sort().join('\\n')
                }
            }
            """);

        rootProject.mainSourceSet().java().writeClass("""
            package hello;

            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello Integration Test");
                }
            }
            """);

        subproject.mainSourceSet().java().writeClass("""
            package hello;

            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello Integration Test");
                }
            }
            """);

        rootProject
                .propertiesFile("versions.props")
                .appendProperty("com.google.guava:guava", "30.1.1-jre")
                .appendProperty("com.palantir.conjure.java:*", "7.21.0");

        rootProject.file("versions.lock").createEmpty();
    }

    @Test
    void prints_all_deps_successfully_without_gcv_with_a_dep_on_root_project(
            GradleInvoker gradle, SubProject subproject) {
        subproject.buildGradle().append("""
            dependencies {
                implementation 'com.palantir.conjure.java:conjure-java-core:7.21.0'
                implementation rootProject
            }
            """);

        gradle.withArgs("printAllDeps").buildsSuccessfully();

        String allDeps = subproject.buildDir().file("allDeps").text().strip();

        String expected = """
            com.atlassian.commonmark:commonmark:0.12.1
            com.fasterxml.jackson.core:jackson-annotations:2.15.3
            com.fasterxml.jackson.core:jackson-core:2.15.3
            com.fasterxml.jackson.core:jackson-databind:2.15.3
            com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3
            com.fasterxml.jackson.datatype:jackson-datatype-guava:2.13.3
            com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.15.3
            com.google.code.findbugs:jsr305:3.0.2
            com.google.errorprone:error_prone_annotations:2.21.1
            com.google.guava:failureaccess:1.0.1
            com.google.guava:guava:32.1.3-jre
            com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava
            com.google.j2objc:j2objc-annotations:2.8
            com.palantir.common:streams:2.2.0
            com.palantir.conjure.java.api:errors:2.38.0
            com.palantir.conjure.java:conjure-java-core:7.21.0
            com.palantir.conjure.java:conjure-lib:7.21.0
            com.palantir.conjure.java:conjure-undertow-lib:7.21.0
            com.palantir.conjure:conjure-api-objects:4.36.0
            com.palantir.conjure:conjure-generator-common:4.36.0
            com.palantir.dialogue:dialogue-target:3.97.0
            com.palantir.goethe:goethe:0.11.0
            com.palantir.human-readable-types:human-readable-types:1.6.0
            com.palantir.ri:resource-identifier:2.6.0
            com.palantir.safe-logging:logger-slf4j:3.6.0
            com.palantir.safe-logging:logger-spi:3.6.0
            com.palantir.safe-logging:logger:3.6.0
            com.palantir.safe-logging:preconditions:3.6.0
            com.palantir.safe-logging:safe-logging:3.6.0
            com.palantir.syntactic-paths:syntactic-paths:0.9.0
            com.palantir.tokens:auth-tokens:3.18.0
            com.squareup:javapoet:1.13.0
            io.undertow:undertow-core:2.2.24.Final
            jakarta.annotation:jakarta.annotation-api:1.3.5
            jakarta.ws.rs:jakarta.ws.rs-api:2.1.6
            org.apache.commons:commons-lang3:3.13.0
            org.checkerframework:checker-qual:3.37.0
            org.glassfish.hk2.external:jakarta.inject:2.6.1
            org.glassfish.hk2:osgi-resource-locator:1.0.3
            org.glassfish.jersey.core:jersey-common:2.40
            org.jboss.logging:jboss-logging:3.4.1.Final
            org.jboss.threads:jboss-threads:3.1.0.Final
            org.jboss.xnio:xnio-api:3.8.7.Final
            org.jboss.xnio:xnio-nio:3.8.7.Final
            org.jetbrains:annotations:24.0.1
            org.slf4j:slf4j-api:1.7.36
            org.wildfly.client:wildfly-client-config:1.0.1.Final
            org.wildfly.common:wildfly-common:1.6.0.Final
            org.yaml:snakeyaml:2.1
            project :
            project :subproject
            """.strip();

        assertThat(allDeps).isEqualTo(expected);
    }

    @Test
    void prints_all_deps_successfully_with_gcv_not_including_dep_on_root_project_via_gcv(
            GradleInvoker gradle, RootProject rootProject, SubProject subproject) {
        System.setProperty("ignoreDeprecations", "true");

        rootProject.buildGradle().plugins().add("com.palantir.consistent-versions");

        subproject.buildGradle().append("""
            dependencies {
                implementation 'com.palantir.conjure.java:conjure-java-core:7.21.0'
            }
            """);

        gradle.withArgs("printAllDeps").buildsSuccessfully();

        String allDeps = subproject.buildDir().file("allDeps").text().strip();

        String expected = """
            com.atlassian.commonmark:commonmark:0.12.1
            com.fasterxml.jackson.core:jackson-annotations:2.15.3
            com.fasterxml.jackson.core:jackson-core:2.15.3
            com.fasterxml.jackson.core:jackson-databind:2.15.3
            com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3
            com.fasterxml.jackson.datatype:jackson-datatype-guava:2.13.3
            com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.15.3
            com.google.code.findbugs:jsr305:3.0.2
            com.google.errorprone:error_prone_annotations:2.21.1
            com.google.guava:failureaccess:1.0.1
            com.google.guava:guava:32.1.3-jre
            com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava
            com.google.j2objc:j2objc-annotations:2.8
            com.palantir.common:streams:2.2.0
            com.palantir.conjure.java.api:errors:2.38.0
            com.palantir.conjure.java:conjure-java-core:7.21.0
            com.palantir.conjure.java:conjure-lib:7.21.0
            com.palantir.conjure.java:conjure-undertow-lib:7.21.0
            com.palantir.conjure:conjure-api-objects:4.36.0
            com.palantir.conjure:conjure-generator-common:4.36.0
            com.palantir.dialogue:dialogue-target:3.97.0
            com.palantir.goethe:goethe:0.11.0
            com.palantir.human-readable-types:human-readable-types:1.6.0
            com.palantir.ri:resource-identifier:2.6.0
            com.palantir.safe-logging:logger-slf4j:3.6.0
            com.palantir.safe-logging:logger-spi:3.6.0
            com.palantir.safe-logging:logger:3.6.0
            com.palantir.safe-logging:preconditions:3.6.0
            com.palantir.safe-logging:safe-logging:3.6.0
            com.palantir.syntactic-paths:syntactic-paths:0.9.0
            com.palantir.tokens:auth-tokens:3.18.0
            com.squareup:javapoet:1.13.0
            io.undertow:undertow-core:2.2.24.Final
            jakarta.annotation:jakarta.annotation-api:1.3.5
            jakarta.ws.rs:jakarta.ws.rs-api:2.1.6
            org.apache.commons:commons-lang3:3.13.0
            org.checkerframework:checker-qual:3.37.0
            org.glassfish.hk2.external:jakarta.inject:2.6.1
            org.glassfish.hk2:osgi-resource-locator:1.0.3
            org.glassfish.jersey.core:jersey-common:2.40
            org.jboss.logging:jboss-logging:3.4.1.Final
            org.jboss.threads:jboss-threads:3.1.0.Final
            org.jboss.xnio:xnio-api:3.8.7.Final
            org.jboss.xnio:xnio-nio:3.8.7.Final
            org.jetbrains:annotations:24.0.1
            org.slf4j:slf4j-api:1.7.36
            org.wildfly.client:wildfly-client-config:1.0.1.Final
            org.wildfly.common:wildfly-common:1.6.0.Final
            org.yaml:snakeyaml:2.1
            project :subproject
            """.strip();

        assertThat(allDeps).isEqualTo(expected);
    }

    @Test
    void when_there_is_no_gcv_platform_or_any_variant_on_the_root_the_root_is_still_selected(
            GradleInvoker gradle, SubProject subproject) {
        subproject.buildGradle().append("""
            def runtimeClasspathCopy = configurations.runtimeClasspath.copy()

            printAllDeps.inputs.property('configuration', runtimeClasspathCopy.name)
            """);

        gradle.withArgs("printAllDeps").buildsSuccessfully();

        String allDeps = subproject.buildDir().file("allDeps").text().strip();

        String expected = """
            project :subproject
            """.strip();

        assertThat(allDeps).isEqualTo(expected);
    }
}
