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

import java.util.Arrays;
import java.util.List;

/**
 * Utility class to maintain and update canonical list versions of gradle to test against.  This helps verify that a plugin
 * is both backwards and forwards compatible.
 *
 *  {@code
 *     @Unroll
 *     def 'runs on version of gradle: #version'() {
 *         when:
 *         gradleVersion = version
 *
 *         then:
 *         ExecutionResult result = runTasksSuccessfully('checkConjureBackCompat')
 *
 *         where:
 *         version << TestDepVersions.GRADLE_VERSIONS
 *     }
 *  }
 */
public final class GradleTestVersions {
    static final List<String> GRADLE_VERSIONS = Arrays.asList("7.6.4", "8.8");

    public static List<String> getGradleVersions() {
        return GRADLE_VERSIONS;
    }

    private GradleTestVersions() {}
}
