/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.utils.projectdependency;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.util.GradleVersion;

/** Utilities for accessing {@link ProjectDependency} APIs across supported Gradle versions. */
public final class ProjectDependencyUtils {
    private static final GradleVersion GRADLE_8_11 = GradleVersion.version("8.11");

    /** Returns the target project path, for example {@code :foo:bar}. */
    public static String getProjectPath(ProjectDependency projectDependency) {
        if (GradleVersion.current().compareTo(GRADLE_8_11) >= 0) {
            return projectDependency.getPath();
        }
        return getLegacyProjectPath(projectDependency);
    }

    private static String getLegacyProjectPath(ProjectDependency projectDependency) {
        try {
            Project dependencyProject = (Project)
                    ProjectDependency.class.getMethod("getDependencyProject").invoke(projectDependency);
            return dependencyProject.getPath();
        } catch (ReflectiveOperationException e) {
            throw new GradleException("Failed to read the project path from " + projectDependency, e);
        }
    }

    private ProjectDependencyUtils() {}
}
