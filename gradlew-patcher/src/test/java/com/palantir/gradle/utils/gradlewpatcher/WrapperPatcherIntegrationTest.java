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

package com.palantir.gradle.utils.gradlewpatcher;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class WrapperPatcherIntegrationTest {

    private static final String PATCH_NAME = "Test patch";
    private static final String PATCH_HEADER = "# >>> " + PATCH_NAME + " >>>";
    private static final String PATCH_FOOTER = "# <<< " + PATCH_NAME + " <<<";

    @BeforeEach
    void setup(RootProject rootProject) {
        String classpathFiles = Arrays.stream(System.getProperty("classpath").split(File.pathSeparator))
                .map(path -> "'" + path + "'")
                .collect(Collectors.joining(", "));

        rootProject.buildGradle().prepend("""
            buildscript {
                dependencies {
                    classpath files(%s)
                }
            }
            """, classpathFiles);

        rootProject.buildGradle().append("""
            import com.palantir.gradle.utils.gradlewpatcher.ImmutableWrapperPatchConfig
            import com.palantir.gradle.utils.gradlewpatcher.WrapperPatchRegistrar

            WrapperPatchRegistrar.register(project, ImmutableWrapperPatchConfig.builder()
                .patchName('%s')
                .patchContent('''\
            # !! Contents within this block are managed by tests !!
            echo "test patch applied"
            '''.stripIndent())
                .patchTaskName('patchTestWrapper')
                .checkTaskName('checkTestWrapper')
                .build())
            """, PATCH_NAME);
    }

    @Test
    void patch_task_patches_gradlew(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("wrapper").buildsSuccessfully();

        rootProject.file("gradlew").assertThat().content().contains("""
            # >>> Test patch >>>
            # !! Contents within this block are managed by tests !!
            echo "test patch applied"
            # <<< Test patch <<<
            """);
    }

    @Test
    void patch_task_is_idempotent(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("wrapper").buildsSuccessfully();
        gradle.withArgs("wrapper").buildsSuccessfully();

        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .containsOnlyOnce(PATCH_HEADER)
                .containsOnlyOnce(PATCH_FOOTER);
    }

    @Test
    void check_task_succeeds_when_patch_is_present(GradleInvoker gradle) {
        gradle.withArgs("wrapper").buildsSuccessfully();
        gradle.withArgs("checkTestWrapper").buildsSuccessfully();
    }

    @Test
    void multiple_patches_coexist(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            WrapperPatchRegistrar.register(project, ImmutableWrapperPatchConfig.builder()
                .patchName('Patch A')
                .patchContent('''\
            echo "patch A"
            '''.stripIndent())
                .patchTaskName('patchA')
                .checkTaskName('checkA')
                .build())

            WrapperPatchRegistrar.register(project, ImmutableWrapperPatchConfig.builder()
                .patchName('Patch B')
                .patchContent('''\
            echo "patch B"
            '''.stripIndent())
                .patchTaskName('patchB')
                .checkTaskName('checkB')
                .build())
            """);

        gradle.withArgs("wrapper", "--parallel").buildsSuccessfully();

        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .contains("# >>> Patch A >>>")
                .contains("# <<< Patch A <<<")
                .contains("# >>> Patch B >>>")
                .contains("# <<< Patch B <<<");

        // Both checks should pass
        gradle.withArgs("checkA", "checkB").buildsSuccessfully();
    }

    @Test
    void multiple_patches_with_parallel_execution(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            WrapperPatchRegistrar.register(project, ImmutableWrapperPatchConfig.builder()
                .patchName('Patch A')
                .patchContent('''\
            echo "patch A"
            '''.stripIndent())
                .patchTaskName('patchA')
                .checkTaskName('checkA')
                .build())

            WrapperPatchRegistrar.register(project, ImmutableWrapperPatchConfig.builder()
                .patchName('Patch B')
                .patchContent('''\
            echo "patch B"
            '''.stripIndent())
                .patchTaskName('patchB')
                .checkTaskName('checkB')
                .build())
            """);

        // Run with --parallel to verify the build service serializes patch tasks
        gradle.withArgs("wrapper", "--parallel").buildsSuccessfully();

        // All three patches (setup + A + B) should be present
        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .contains(PATCH_HEADER)
                .contains(PATCH_FOOTER)
                .contains("# >>> Patch A >>>")
                .contains("# <<< Patch A <<<")
                .contains("# >>> Patch B >>>")
                .contains("# <<< Patch B <<<");

        // All check tasks should pass
        gradle.withArgs("checkTestWrapper", "checkA", "checkB").buildsSuccessfully();
    }

    @Test
    void check_task_fails_when_patch_content_is_modified(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("wrapper").buildsSuccessfully();
        gradle.withArgs("checkTestWrapper").buildsSuccessfully();

        // Modify the patch content inside the markers
        rootProject
                .file("gradlew")
                .edit(content -> content.replace("echo \"test patch applied\"", "echo \"tampered\""));

        InvocationResult result = gradle.withArgs("checkTestWrapper").buildsWithFailure();
        assertThat(result).task(":checkTestWrapper").failed();
    }

    @Test
    void patch_task_re_runs_after_wrapper_regenerates(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("wrapper").buildsSuccessfully();

        // Running wrapper again regenerates gradlew, so patch must re-run
        InvocationResult secondRun = gradle.withArgs("wrapper").buildsSuccessfully();
        assertThat(secondRun).task(":patchTestWrapper").succeeded();

        // Patch should still be present
        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .containsOnlyOnce(PATCH_HEADER)
                .containsOnlyOnce(PATCH_FOOTER);
    }

    @Test
    void check_task_fails_when_patch_is_missing(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("wrapper").buildsSuccessfully();

        // Remove the patch block from gradlew
        rootProject
                .file("gradlew")
                .edit(content -> content.replaceAll("(?s)" + PATCH_HEADER + ".*?" + PATCH_FOOTER + "\\n", ""));

        InvocationResult result = gradle.withArgs("checkTestWrapper").buildsWithFailure();
        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .doesNotContain(PATCH_HEADER)
                .doesNotContain(PATCH_FOOTER);
        assertThat(result).output().contains("Gradle Wrapper script is out of date");
    }
}
