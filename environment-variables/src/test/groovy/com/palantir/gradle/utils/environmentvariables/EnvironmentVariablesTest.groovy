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

package com.palantir.gradle.utils.environmentvariables

import nebula.test.IntegrationSpec

class EnvironmentVariablesTest extends IntegrationSpec {
    def setup() {
        /* language=gradle */
        buildFile << '''
            import com.palantir.gradle.utils.environmentvariables.EnvironmentVariables

            public abstract class TestClass {
                @Nested
                abstract EnvironmentVariables getEnvironmentVariables()
            }

            def variables = objects.newInstance(TestClass).environmentVariables
            println('Variable: ' + variables.envVarOrFromTestingProperty('VARIABLE').getOrNull())
            println('isCircleNode0OrLocal: ' + variables.isCircleNode0OrLocal().getOrNull())
        '''.stripIndent(true)
    }

    def 'can get testing variables'() {
        when:
        def stdout = runTasksSuccessfully('help',
                '-P__TESTING=true', '-P__TESTING_VARIABLE=test').standardOutput

        then:
        stdout.contains("Variable: test")
    }

    def 'can get environment variables'() {
        when:
        def stdout = runTasksSuccessfully('help').standardOutput

        then:
        stdout.contains("Variable: actual value")
    }

    def 'isCircleNode0OrLocal returns true on circle node 0'() {
        when:
        def stdout = runTasksSuccessfully('help',
                '-P__TESTING=true', '-P__TESTING_CIRCLE_NODE_INDEX=0').standardOutput

        then:
        stdout.contains("isCircleNode0OrLocal: true")
    }

    def 'isCircleNode0OrLocal returns false on circle node 1'() {
        when:
        def stdout = runTasksSuccessfully('help',
                '-P__TESTING=true', '-P__TESTING_CIRCLE_NODE_INDEX=1').standardOutput

        then:
        stdout.contains("isCircleNode0OrLocal: false")
    }

    def 'isCircleNode0OrLocal returns true locally'() {
        when:
        def stdout = runTasksSuccessfully('help',
                '-P__TESTING=true').standardOutput

        then:
        stdout.contains("isCircleNode0OrLocal: true")
    }

}
