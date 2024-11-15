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
        writeHelloWorld('com.testing')

    }

    def 'ignoreDeprecations automatically set'() {
        setup:
        //language=gradle
        buildFile.text = """
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                    gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }
            
                dependencies {
                    classpath 'com.palantir.gradle.externalpublish:gradle-external-publish-plugin:1.19.0'
                    classpath 'com.gradle.publish:plugin-publish-plugin:1.3.0'
                }
            }

            apply plugin: 'groovy'
            apply plugin: 'com.palantir.external-publish-gradle-plugin'
            apply plugin: 'com.palantir.gradle.integration-test-utils'
            
            repositories {
                mavenCentral()
            }

            dependencies {
                implementation gradleApi()
                testImplementation '${resolve("org.junit.jupiter:junit-jupiter")}'
                testImplementation '${resolve("com.netflix.nebula:nebula-test")}'
            }
        """.stripIndent(true)

        //language=groovy
        file('src/test/groovy/com/testing/HelloWorldSpec.groovy') << '''
            package com.testing

            import nebula.test.IntegrationSpec

            class HelloWorldSpec extends IntegrationSpec {
                def setup() {
                    //language=gradle
                    buildFile << """
                        buildscript {
                            repositories {
                                mavenCentral()
                            }
                            dependencies {
                                // This version causes deprecation warnings in gradle 8 for gradle 9
                                classpath 'com.palantir.gradle.consistentversions:gradle-consistent-versions:2.27.0'
                            }
                        }
                        apply plugin: 'java'
                        apply plugin: 'com.palantir.consistent-versions'
                    """.stripIndent(true)
                }

                def 'someTest'() {
                    when:
                    def result = runTasks('dependencies')

                    then:
                    println result.output
                    result.success
                }
            }
        '''.stripIndent(true)

        //writeUnitTest()

        when:
        def result = runTasks('test')

        then:
        println "************************************************std error follows******************************************"
        println result.standardError
        println "************************************************std out follows******************************************"
        println result.standardOutput
        result.success
    }

    def 'override gradle testing versions'() {
        expect:
        project.plugins.hasPlugin(GradleTestUtilsPlugin)
    }
}
