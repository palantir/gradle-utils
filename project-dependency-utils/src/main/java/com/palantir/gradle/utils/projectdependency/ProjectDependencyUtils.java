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

import java.lang.reflect.Method;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.util.GradleVersion;

/**
 * Reads the path of a {@link ProjectDependency} across Gradle 8 and 9.
 *
 * <p>{@link ProjectDependency#getPath()} only exists on Gradle 8.11+, while the older
 * {@code ProjectDependency.getDependencyProject()} was removed in Gradle 9. To support consumers on either side of
 * that line, the pre-8.11 fallback is invoked <em>reflectively</em> rather than referenced statically. This is
 * deliberate: a static reference to the removed {@code getDependencyProject()} would stop this module compiling once
 * gradle-utils bumps its own wrapper to Gradle 9, pinning the whole repo to Gradle 8. Reflection keeps this module
 * compiling on both Gradle 8 and Gradle 9 while still working at runtime against Gradle &lt; 8.11.
 *
 * <p>Once every consumer is on Gradle 8.11+, the reflective fallback can be deleted in favour of a plain
 * {@code projectDependency.getPath()}.
 */
public final class ProjectDependencyUtils {
    private static final GradleVersion GRADLE_8_11 = GradleVersion.version("8.11");

    /** Get the project path (e.g. {@code :foo:bar}) of a {@link ProjectDependency}. */
    public static String getProjectPath(ProjectDependency projectDependency) {
        if (GradleVersion.current().compareTo(GRADLE_8_11) >= 0) {
            return projectDependency.getPath();
        }
        return getProjectPathReflectively(projectDependency);
    }

    // Gradle < 8.11: getPath() does not exist, but the (now-removed-in-9) getDependencyProject() does. We cannot
    // reference it statically without breaking compilation on Gradle 9, so we reach it reflectively.
    private static String getProjectPathReflectively(ProjectDependency projectDependency) {
        try {
            Method getDependencyProject = ProjectDependency.class.getMethod("getDependencyProject");
            Project dependencyProject = (Project) getDependencyProject.invoke(projectDependency);
            return dependencyProject.getPath();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to read the project path from a ProjectDependency on Gradle < 8.11", e);
        }
    }

    private ProjectDependencyUtils() {}
}
