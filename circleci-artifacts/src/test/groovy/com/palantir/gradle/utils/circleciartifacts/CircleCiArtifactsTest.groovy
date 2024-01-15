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

package com.palantir.gradle.utils.circleciartifacts

import nebula.test.IntegrationSpec

class CircleCiArtifactsTest extends IntegrationSpec {

    void setup() {
        /* language=gradle */
        buildFile << """
            import com.palantir.gradle.utils.circleciartifacts.CircleCiArtifacts
            
            public abstract class CircleCiArtifactsTask extends DefaultTask {
              @Nested
              abstract CircleCiArtifacts getCircleCiArtifacts();
            }
            
            tasks.register("printCircleCiLocation", CircleCiArtifactsTask) {
                doLast { task ->
                    def artifactLocation = task.circleCiArtifacts.resolveArtifactLocation('location/in/artifacts')
                    if (artifactLocation.isPresent()) {
                        println "Physical path: \${artifactLocation.get().physicalPath()}"
                        println "External location: \${artifactLocation.get().externalLocation()}"
                    } else {
                        println "Not in Circle, empty artifact location"
                    }
                }
            }
        """.stripIndent(true)
    }

    def "can use CircleCiArtifacts when the right environment variables are set"() {
        given:
        def fakeCircleArtifacts = directory("build/circle-artifacts")
        file('gradle.properties') << """
            __TESTING = true
            __TESTING_CI=true
            __TESTING_CIRCLE_ARTIFACTS=${projectDir.relativePath(fakeCircleArtifacts)}
            __TESTING_CIRCLE_PROJECT_USERNAME=palantir
            __TESTING_CIRCLE_PROJECT_REPONAME=gradle-utils
            __TESTING_CIRCLE_BUILD_NUM=1234
            __TESTING_CIRCLE_NODE_INDEX=2345
        """.stripIndent(true)

        when: 'running the task to print the path and location'
        def result = runTasksSuccessfully('printCircleCiLocation')

        then: 'location is as we expect'
        result.standardOutput.find "Physical path: .*/build/circle-artifacts/location/in/artifacts"
        result.standardOutput.find "External location: palantir/gradle-utils/1234/artifacts/2345/.*/location/in/artifacts"
    }

    def "empty property if we're not in circle"() {
        given:
        file('gradle.properties') << """
            __TESTING = true
        """.stripIndent(true)

        when: 'running the task to print the path and location'
        def result = runTasksSuccessfully('printCircleCiLocation')

        then: 'we get a missing location'
        result.standardOutput.contains "Not in Circle, empty artifact location"
    }
}
