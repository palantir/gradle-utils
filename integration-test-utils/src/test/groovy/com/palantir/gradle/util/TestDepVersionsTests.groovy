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
import nebula.test.functional.ExecutionResult
import static com.palantir.gradle.util.TestDepVersions.resolve

class TestDepVersionsTests extends IntegrationSpec {
    def setup() {
        System.setProperty('ignoreDeprecations', 'true')
        TestDepVersions.setVersionsDir(projectDir.toPath())
        writeHelloWorld('com.testing')

        //write versions.props - I am aware of the irony that I am using a hardcoded version within the test file
        file('versions.props') << """
            com.palantir.sls-packaging:* = 7.69.0
        """.stripIndent()
        file('versions.lock') << """
            com.palantir.sls-packaging:gradle-sls-packaging-api:7.69.0 (1 constraints: f2133970)
        """.stripIndent()

        buildFile << """
            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath '${resolve('com.palantir.sls-packaging:gradle-sls-packaging')}'
                }
            }

            group = 'org.test'
            version = '1.0.0'

            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-java-service-distribution'

            distribution {
                serviceName 'sample-service'
                mainClass 'com.testing.hello'
            }
        """.stripIndent()
    }

    def 'test fully specified dep'() {

        when:
        ExecutionResult result = runTasksSuccessfully('tasks')

        then:
        result.success

    }

    def 'test only group'() {

        when:
        ExecutionResult result = runTasksSuccessfully('tasks')

        then:
        result.success

    }

    def 'test missing dep'() {
        buildFile << """
            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath '${resolve('com.palantir.sls-packaging:gradle-sls-packaging')}'
                }
            }
        """

        when:
        ExecutionResult result = runTasksWithFailure('tasks')

        then:
        !result.success

    }

}
