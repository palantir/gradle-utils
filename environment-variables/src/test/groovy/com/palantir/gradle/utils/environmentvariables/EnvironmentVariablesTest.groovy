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
import nebula.test.functional.ExecutionResult

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
            println('Variable: ' + variables.envVarOrFromTestingProperty('VARIABLE').get())
        '''.stripIndent(true)
    }
    def 'can get testing variables'() {
        when:
        ExecutionResult result = runTasksSuccessfully('tasks', '-P__TESTING=true', '-P__TESTING_VARIABLE=test')

        then:
        result.standardOutput.contains("Variable: test")
    }

    def 'can get environment variables'() {
        when:
        ExecutionResult result = runTasksSuccessfully('tasks')

        then:
        result.standardOutput.contains("Variable: actual value")
    }

}
