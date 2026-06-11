/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.utils.gradlewpatcher;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class WrapperPatcherIntegrationTest {

    private static final String PATCH_HEADER = "# >>> Test patch >>>";
    private static final String PATCH_FOOTER = "# <<< Test patch <<<";

    @BeforeEach
    void setup(RootProject rootProject) {
        String jarPath = Optional.ofNullable(System.getProperty("jarPath"))
                .orElseThrow(() -> new RuntimeException("expected jarPath to be set"));

        rootProject.buildGradle().prepend("""
            buildscript {
                dependencies {
                    classpath files('%s')
                }
            }
            """, jarPath);

        rootProject.buildGradle().append("""
            import com.palantir.gradle.utils.gradlewpatcher.ImmutableWrapperPatchConfig
            import com.palantir.gradle.utils.gradlewpatcher.WrapperPatchRegistrar

            WrapperPatchRegistrar.register(project, ImmutableWrapperPatchConfig.builder()
                .patchHeader('%s')
                .patchFooter('%s')
                .patchResource('test-wrapper-patch.sh')
                .patchTaskName('patchTestWrapper')
                .checkTaskName('checkTestWrapper')
                .build())
            """, PATCH_HEADER, PATCH_FOOTER);
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
    void check_task_fails_when_patch_is_missing(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("wrapper").buildsSuccessfully();

        // Remove the patch block from gradlew
        rootProject
                .file("gradlew")
                .edit(content -> content.replaceAll("(?s)" + PATCH_HEADER + ".*?" + PATCH_FOOTER + "\\n", ""));

        InvocationResult result = gradle.withArgs("checkTestWrapper").buildsWithFailure();
        assertThat(result).output().contains("Gradle Wrapper script is out of date");
    }
}
