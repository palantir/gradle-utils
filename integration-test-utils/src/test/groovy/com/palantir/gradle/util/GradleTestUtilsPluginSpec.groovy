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

package com.palantir.gradle.util

import nebula.test.IntegrationSpec

import static com.palantir.gradle.util.TestDepVersions.resolve

class GradleTestUtilsPluginSpec extends IntegrationSpec {
    def setup() {
        TestDepVersions.setVersionsDir(projectDir.toPath())

        //language=properties
        file('versions.props') << """
            com.palantir.sls-packaging:* = 7.69.0
            # This version causes deprecation warnings in gradle 8 for gradle 9
            com.palantir.gradle.consistentversions:gradle-consistent-versions = 2.27.0
        """.stripIndent(true)
        file('versions.lock') << """
            com.palantir.sls-packaging:gradle-sls-packaging-api:7.69.0 (1 constraints: f2133970)
            com.palantir.gradle.consistentversions:gradle-consistent-versions:2.27.0 (1 constraints: 3d05483b)
        """.stripIndent(true)

        //language=gradle
        buildFile << """
            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath '${resolve('com.palantir.sls-packaging:gradle-sls-packaging')}'
                    classpath '${resolve('com.palantir.gradle.consistentversions:gradle-consistent-versions')}'
                }
            }

            apply plugin: 'com.palantir.consistent-versions'

            group = 'org.test'
            version = '1.0.0'

            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-java-service-distribution'

            distribution {
                serviceName 'sample-service'
                mainClass 'com.testing.hello'
            }
        """.stripIndent(true)

        writeHelloWorld('com.testing')

    }

    def 'ignoreDeprecations automatically set'() {
        setup:
        //language=gradle
        buildFile << """
            apply plugin: 'com.palantir.gradle-test-utils'
        """.stripIndent(true)

        when:
        def result = runTasksSuccessfully('dependencies')

        then:
        println result.output
        !result.output.contains('Deprecation warnings were found')
    }

    def 'override gradle testing versions'() {
        expect:
        project.plugins.hasPlugin(GradleTestUtilsPlugin)
    }
}
