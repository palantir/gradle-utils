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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class WrapperPatcherIntegrationTest {

    private static final String PATCH_NAME = "Test patch";
    private static final String PATCH_HEADER = "# >>> " + PATCH_NAME + " >>>";
    private static final String PATCH_FOOTER = "# <<< " + PATCH_NAME + " <<<";
    private static final String MANAGED_HEADER = "# >>> Managed patches >>>";
    private static final String MANAGED_FOOTER = "# <<< Managed patches <<<";

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.gradlew-patcher");
    }

    @Nested
    class WithTestPatch {

        @BeforeEach
        void setup(RootProject rootProject) {
            rootProject.buildGradle().append("""
                wrapperPatches.patch('test-patch') {
                    patchName = '%s'
                    content = '''\
                        # !! Contents within this block are managed by tests !!
                        echo "test patch applied"
                        '''.stripIndent()
                }
                """, PATCH_NAME);
        }

        @Test
        void patch_task_patches_gradlew(GradleInvoker gradle, RootProject rootProject) {
            gradle.withArgs("wrapper").buildsSuccessfully();

            rootProject.file("gradlew").assertThat().content().contains("""
                # >>> Managed patches >>>
                # >>> Test patch >>>
                # !! Contents within this block are managed by tests !!
                echo "test patch applied"
                # <<< Test patch <<<
                # <<< Managed patches <<<
                """);
        }

        @Test
        void patch_task_is_idempotent(GradleInvoker gradle, RootProject rootProject) {
            InvocationResult result = gradle.withArgs("wrapper").buildsSuccessfully();
            result.assertThat().task(":patchGradlewWrapper").succeeded();

            String gradlewAfterFirstPatch = rootProject.file("gradlew").text();

            gradle.withArgs("patchGradlewWrapper").buildsSuccessfully();
            result.assertThat().task(":patchGradlewWrapper").succeeded();

            rootProject.file("gradlew").assertThat().content().isEqualTo(gradlewAfterFirstPatch);

            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .containsOnlyOnce(MANAGED_HEADER)
                    .containsOnlyOnce(MANAGED_FOOTER)
                    .containsOnlyOnce(PATCH_HEADER)
                    .containsOnlyOnce(PATCH_FOOTER);
        }

        @Test
        void check_task_succeeds_when_patch_is_present(GradleInvoker gradle) {
            gradle.withArgs("wrapper").buildsSuccessfully();
            gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
        }

        @Test
        void check_task_fails_when_patch_content_is_modified(GradleInvoker gradle, RootProject rootProject) {
            gradle.withArgs("wrapper").buildsSuccessfully();
            gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();

            rootProject
                    .file("gradlew")
                    .edit(content -> content.replace("echo \"test patch applied\"", "echo \"tampered\""));

            InvocationResult result = gradle.withArgs("checkGradlewWrapper").buildsWithFailure();
            result.assertThat().task(":checkGradlewWrapper").failed();
            result.assertThat()
                    .output()
                    .contains("Gradle Wrapper script is out of date: managed patches block does not match expected"
                            + " content.");
        }

        @Test
        void multiple_patches_coexist(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                wrapperPatches.patch('patch-a') {
                    patchName = 'Patch A'
                    content = 'echo "patch A"'
                }
                wrapperPatches.patch('patch-b') {
                    patchName = 'Patch B'
                    content = 'echo "patch B"'
                }
                """);

            gradle.withArgs("wrapper").buildsSuccessfully();

            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .containsSubsequence(
                            MANAGED_HEADER,
                            PATCH_HEADER,
                            PATCH_FOOTER,
                            "# >>> Patch A >>>",
                            "# <<< Patch A <<<",
                            "# >>> Patch B >>>",
                            "# <<< Patch B <<<",
                            MANAGED_FOOTER);

            gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
        }

        @Test
        void patches_applied_in_topological_order(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                wrapperPatches.patch('patch-a') {
                    patchName = 'Patch A'
                    content = 'echo "patch A"'
                }
                wrapperPatches.patch('patch-b') {
                    patchName = 'Patch B'
                    content = 'echo "patch B"'
                    mustRunBefore = ['patch-a']
                }
                """);

            gradle.withArgs("wrapper").buildsSuccessfully();

            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .as("content contains B before A, all within managed block")
                    .containsSubsequence(
                            MANAGED_HEADER,
                            PATCH_HEADER,
                            PATCH_FOOTER,
                            "# >>> Patch B >>>",
                            "# <<< Patch B <<<",
                            "# >>> Patch A >>>",
                            "# <<< Patch A <<<",
                            MANAGED_FOOTER);
        }

        @Test
        void new_patch_registered_after_existing_patch_already_applied(GradleInvoker gradle, RootProject rootProject) {
            // First run: only the setup patch is applied
            gradle.withArgs("wrapper").buildsSuccessfully();
            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .contains(PATCH_HEADER)
                    .contains(PATCH_FOOTER)
                    .doesNotContain("# >>> New patch >>>");

            // Register a new patch after the first one is already written to gradlew
            rootProject.buildGradle().append("""
                wrapperPatches.patch('new-patch') {
                    patchName = 'New patch'
                    content = 'echo "new patch applied"'
                    mustRunAfter = ['test-patch']
                }
                """);

            // Second run: both patches should be present, new patch after the original
            gradle.withArgs("wrapper").buildsSuccessfully();

            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .containsOnlyOnce(MANAGED_HEADER)
                    .containsOnlyOnce(MANAGED_FOOTER)
                    .containsOnlyOnce(PATCH_HEADER)
                    .containsOnlyOnce(PATCH_FOOTER)
                    .containsOnlyOnce("# >>> New patch >>>")
                    .containsOnlyOnce("# <<< New patch <<<")
                    .as("patches exist only once in the correct order")
                    .containsSubsequence(
                            MANAGED_HEADER,
                            PATCH_HEADER,
                            PATCH_FOOTER,
                            "# >>> New patch >>>",
                            "echo \"new patch applied\"",
                            "# <<< New patch <<<",
                            MANAGED_FOOTER);

            gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
        }

        @Test
        void new_patch_registered_before_existing_patch_already_applied(GradleInvoker gradle, RootProject rootProject) {
            // First run: only the setup patch is applied
            gradle.withArgs("wrapper").buildsSuccessfully();
            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .contains(PATCH_HEADER)
                    .contains(PATCH_FOOTER)
                    .doesNotContain("# >>> New patch >>>");

            // Register a new patch that must run before the existing one
            rootProject.buildGradle().append("""
                wrapperPatches.patch('new-patch') {
                    patchName = 'New patch'
                    content = 'echo "new patch applied"'
                    mustRunBefore = ['test-patch']
                }
                """);

            // Second run: both patches should be present, new patch before the original
            gradle.withArgs("wrapper").buildsSuccessfully();

            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .containsOnlyOnce(MANAGED_HEADER)
                    .containsOnlyOnce(MANAGED_FOOTER)
                    .containsOnlyOnce(PATCH_HEADER)
                    .containsOnlyOnce(PATCH_FOOTER)
                    .containsOnlyOnce("# >>> New patch >>>")
                    .containsOnlyOnce("# <<< New patch <<<")
                    .containsSubsequence(
                            MANAGED_HEADER,
                            "# >>> New patch >>>",
                            "echo \"new patch applied\"",
                            "# <<< New patch <<<",
                            PATCH_HEADER,
                            PATCH_FOOTER,
                            MANAGED_FOOTER);

            gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
        }

        @Test
        void legacy_patch_without_managed_block_is_migrated(GradleInvoker gradle, RootProject rootProject) {
            // First run produces a gradlew with the managed block
            gradle.withArgs("wrapper").buildsSuccessfully();

            // Simulate a legacy gradlew by stripping the managed header/footer but keeping the inner patch
            rootProject
                    .file("gradlew")
                    .edit(content -> content.replace(MANAGED_HEADER + "\n", "").replace(MANAGED_FOOTER + "\n", ""));

            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .doesNotContain(MANAGED_HEADER)
                    .doesNotContain(MANAGED_FOOTER)
                    .contains(PATCH_HEADER)
                    .contains(PATCH_FOOTER);

            // Re-running the patch task should wrap it in the managed block
            gradle.withArgs("patchGradlewWrapper").buildsSuccessfully();

            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .containsOnlyOnce(MANAGED_HEADER)
                    .containsOnlyOnce(MANAGED_FOOTER)
                    .containsSubsequence(MANAGED_HEADER, PATCH_HEADER, PATCH_FOOTER, MANAGED_FOOTER);

            gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
        }

        @Test
        void unmanaged_legacy_patch_is_removed(GradleInvoker gradle, RootProject rootProject) {
            // First run produces a gradlew with the managed block containing the test patch
            gradle.withArgs("wrapper").buildsSuccessfully();

            // Simulate a previously-registered patch inside the managed block that is no longer declared
            rootProject
                    .file("gradlew")
                    .edit(content -> content.replace(
                            PATCH_HEADER,
                            "# >>> Old patch >>>\necho \"old stuff\"\n# <<< Old patch <<<\n" + PATCH_HEADER));

            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .contains("# >>> Old patch >>>")
                    .contains("# <<< Old patch <<<");

            // Re-running the patch task should not preserve the unmanaged old patch
            gradle.withArgs("patchGradlewWrapper").buildsSuccessfully();

            rootProject
                    .file("gradlew")
                    .assertThat()
                    .content()
                    .doesNotContain("# >>> Old patch >>>")
                    .doesNotContain("# <<< Old patch <<<")
                    .doesNotContain("echo \"old stuff\"")
                    .containsOnlyOnce(MANAGED_HEADER)
                    .containsOnlyOnce(MANAGED_FOOTER)
                    .containsSubsequence(MANAGED_HEADER, PATCH_HEADER, PATCH_FOOTER, MANAGED_FOOTER);

            gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
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

    @Test
    void check_task_fails_when_patch_is_missing(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("wrapper").buildsSuccessfully();

        // Register a patch and check without applying it
        rootProject.buildGradle().append("""
            wrapperPatches.patch('temp-patch') {
                patchName = 'Temp patch'
                content = 'echo "temporary"'
            }
            """);

        InvocationResult result = gradle.withArgs("checkGradlewWrapper").buildsWithFailure();
        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .doesNotContain(MANAGED_HEADER)
                .doesNotContain(MANAGED_FOOTER);
        result.assertThat().output().contains("Gradle Wrapper script is out of date");
    }

    @Test
    void removing_all_patches_cleans_up_managed_block(GradleInvoker gradle, RootProject rootProject) {
        // Register a patch and apply it
        rootProject.buildGradle().append("""
            wrapperPatches.patch('temp-patch') {
                patchName = 'Temp patch'
                content = 'echo "temporary"'
            }
            """);

        gradle.withArgs("wrapper").buildsSuccessfully();
        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .contains(MANAGED_HEADER)
                .contains(MANAGED_FOOTER);

        // Remove the patch declaration from build.gradle
        rootProject
                .file("build.gradle")
                .edit(content -> content.replaceAll("(?s)wrapperPatches\\.patch\\('temp-patch'\\).*?\\}\n", ""));

        // Re-running should strip the stale managed block
        gradle.withArgs("patchGradlewWrapper").buildsSuccessfully();

        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .doesNotContain(MANAGED_HEADER)
                .doesNotContain(MANAGED_FOOTER)
                .doesNotContain("echo \"temporary\"");

        gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
    }

    @Test
    void no_patches_does_not_write_managed_block(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("wrapper").buildsSuccessfully();

        rootProject
                .file("gradlew")
                .assertThat()
                .content()
                .doesNotContain(MANAGED_HEADER)
                .doesNotContain(MANAGED_FOOTER);

        gradle.withArgs("checkGradlewWrapper").buildsSuccessfully();
    }
}
