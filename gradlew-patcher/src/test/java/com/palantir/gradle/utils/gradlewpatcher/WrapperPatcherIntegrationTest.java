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
            import com.palantir.gradle.utils.gradlewpatcher.WrapperPatcherPlugin
            import com.palantir.gradle.utils.gradlewpatcher.PatchDeclaration

            apply plugin: WrapperPatcherPlugin

            def testPatch = objects.newInstance(PatchDeclaration)
            testPatch.id.set('test-patch')
            testPatch.patchName.set('%s')
            testPatch.content.set('''\
            # !! Contents within this block are managed by tests !!
            echo "test patch applied"
            '''.stripIndent())
            wrapperPatches.patches.add(testPatch)
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
        gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
    }

    @Test
    void check_task_fails_when_patch_is_missing(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("wrapper").buildsSuccessfully();

        rootProject
                .file("gradlew")
                .edit(content -> content.replaceAll("(?s)" + PATCH_HEADER + ".*?" + PATCH_FOOTER + "\\n", ""));

        InvocationResult result = gradle.withArgs("checkGradlewWrapper").buildsWithFailure();
        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .doesNotContain(PATCH_HEADER)
                .doesNotContain(PATCH_FOOTER);
        assertThat(result).output().contains("Gradle Wrapper script is out of date");
    }

    @Test
    void check_task_fails_when_patch_content_is_modified(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("wrapper").buildsSuccessfully();
        gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();

        rootProject
                .file("gradlew")
                .edit(content -> content.replace("echo \"test patch applied\"", "echo \"tampered\""));

        InvocationResult result = gradle.withArgs("checkGradlewWrapper").buildsWithFailure();
        assertThat(result).task(":checkGradlewWrapper").failed();
    }

    @Test
    void multiple_patches_coexist(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            def patchA = objects.newInstance(PatchDeclaration)
            patchA.id.set('patch-a')
            patchA.patchName.set('Patch A')
            patchA.content.set('echo "patch A"')
            wrapperPatches.patches.add(patchA)

            def patchB = objects.newInstance(PatchDeclaration)
            patchB.id.set('patch-b')
            patchB.patchName.set('Patch B')
            patchB.content.set('echo "patch B"')
            wrapperPatches.patches.add(patchB)
            """);

        gradle.withArgs("wrapper").buildsSuccessfully();

        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .contains("# >>> Patch A >>>")
                .contains("# <<< Patch A <<<")
                .contains("# >>> Patch B >>>")
                .contains("# <<< Patch B <<<")
                .contains(PATCH_HEADER)
                .contains(PATCH_FOOTER);

        gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
    }

    @Test
    void patches_applied_in_topological_order(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            def patchA = objects.newInstance(PatchDeclaration)
            patchA.id.set('patch-a')
            patchA.patchName.set('Patch A')
            patchA.content.set('echo "patch A"')
            patchA.mustRunBefore.set(['patch-b'])
            wrapperPatches.patches.add(patchA)

            def patchB = objects.newInstance(PatchDeclaration)
            patchB.id.set('patch-b')
            patchB.patchName.set('Patch B')
            patchB.content.set('echo "patch B"')
            wrapperPatches.patches.add(patchB)
            """);

        gradle.withArgs("wrapper").buildsSuccessfully();

        // Verify ordering by checking content contains A before B
        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .containsSubsequence(
                        "# >>> Patch A >>>", "# <<< Patch A <<<",
                        "# >>> Patch B >>>", "# <<< Patch B <<<");
    }

    @Test
    void patch_task_is_eventually_up_to_date(GradleInvoker gradle) {
        gradle.withArgs("wrapper").buildsSuccessfully();

        // The patch task reads and writes the same gradlew file, so @InputFile always differs
        // from the previous execution's input (pre-patch vs post-patch content).
        InvocationResult secondRun = gradle.withArgs("patchGradlewWrapper").buildsSuccessfully();
        assertThat(secondRun).task(":patchGradlewWrapper").succeeded();

        InvocationResult thirdRun = gradle.withArgs("patchGradlewWrapper").buildsSuccessfully();
        assertThat(thirdRun).task(":patchGradlewWrapper").upToDate();
    }

    @Test
    void check_task_is_up_to_date_on_second_run(GradleInvoker gradle) {
        gradle.withArgs("wrapper").buildsSuccessfully();
        gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();

        InvocationResult secondRun = gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
        assertThat(secondRun).task(":checkGradlewWrapper").upToDate();
    }
}
