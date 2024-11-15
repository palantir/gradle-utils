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

package com.palantir.gradle.util;

import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;

public abstract class GradleTestUtilsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // add task to write the version resource file
        // then update GradleTestVersions to read that file

        project.getTasks().withType(Test.class).configureEach(test -> {
            // add system properties when running tests
            GradleTestUtilsExtension testUtilsExt = project.getExtensions().getByType(GradleTestUtilsExtension.class);
            String versions = String.join(",", testUtilsExt.getGradleVersions().get());
            Map<String, String> systemProperties = new HashMap<>();
            systemProperties.put(GradleTestVersions.TEST_GRADLE_VERSIONS_SYSTEM_PROPERTY, versions);

            if (testUtilsExt.ignoreGradleDeprecations().get()) {
                // from
                // https://github.com/nebula-plugins/nebula-test/blob/main/src/main/groovy/nebula/test/IntegrationBase.groovy
                systemProperties.put("ignoreDeprecations", "true");
            }

            test.systemProperties(systemProperties);
        });
    }
}
