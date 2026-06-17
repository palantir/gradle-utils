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

import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Composite Gradle task that patches or validates all registered patch blocks in the gradlew wrapper script.
 *
 * <p>When {@code generate} is {@code true}, the task strips all existing patches and re-inserts them
 * in topological order. When {@code false}, the task validates that all patches are present with the
 * expected content and in the correct relative order.
 */
public abstract class WrapperPatcherTask extends DefaultTask {

    private static final Logger log = Logging.getLogger(WrapperPatcherTask.class);
    private static final String COMMENT_BLOCK = "###";

    /** Patches in topologically sorted order. */
    @Nested
    public abstract ListProperty<OrderedPatch> getOrderedPatches();

    @Input
    public abstract Property<Boolean> getGenerate();

    @InputFile
    public abstract RegularFileProperty getOriginalGradlewScript();

    @OutputFile
    public abstract RegularFileProperty getPatchedGradlewScript();

    public WrapperPatcherTask() {
        getGenerate().convention(false);
    }

    @TaskAction
    public final void action() {
        if (getGenerate().get()) {
            log.lifecycle("Patching the gradle wrapper files.");
            patchGradlewContent();
        } else {
            checkContainsPatches();
        }
    }

    private void patchGradlewContent() {
        List<OrderedPatch> patches = getOrderedPatches().get();

        File originalGradlewScript = getOriginalGradlewScript().getAsFile().get();
        List<String> lines = WrapperPatchHelper.readAllLines(originalGradlewScript.toPath());

        List<String> patchNames =
                patches.stream().map(patch -> patch.getName().get()).toList();

        // Strip all existing patches (including stale managed block)
        lines = WrapperPatchHelper.getLinesWithoutPatches(lines, patchNames);

        if (patches.isEmpty()) {
            WrapperPatchHelper.writeContentWithoutPatches(
                    getPatchedGradlewScript().getAsFile().get().toPath(), lines);
            return;
        }

        // Find insertion point
        int insertIndex = getInsertLineIndex(lines);

        List<String> allPatchLines = patches.stream()
                .flatMap(patch ->
                        WrapperPatchHelper.getPatchLinesWithHeader(
                                patch.getContent().get(), patch.getName().get())
                                .stream())
                .toList();
        List<String> managedBlock = WrapperPatchHelper.wrapInManagedBlock(allPatchLines);

        WrapperPatchHelper.writeContentWithPatch(
                getPatchedGradlewScript().getAsFile().get().toPath(), lines, managedBlock, insertIndex);
    }

    private void checkContainsPatches() {
        List<OrderedPatch> patches = getOrderedPatches().get();

        File gradlewFile = getOriginalGradlewScript().get().getAsFile();
        List<String> lines = WrapperPatchHelper.readAllLines(gradlewFile.toPath());

        List<String> expectedPatchLines = patches.stream()
                .flatMap(patch ->
                        WrapperPatchHelper.getPatchLinesWithHeader(
                                patch.getContent().get(), patch.getName().get())
                                .stream())
                .toList();
        List<String> expectedBlock = WrapperPatchHelper.wrapInManagedBlock(expectedPatchLines);

        Optional<WrapperPatchHelper.PatchLineNumbers> managedRange =
                WrapperPatchHelper.getPatchLineNumbers(lines, WrapperPatchHelper.MANAGED_PATCH_NAME);

        if (managedRange.isEmpty() && patches.isEmpty()) {
            return;
        }
        if (managedRange.isEmpty()) {
            throw new ExceptionWithSuggestion("""
                Gradle Wrapper script is out of date: managed patches block is missing.
                Please run `./gradlew patchGradlewWrapper` to fix.
                """, "./gradlew patchGradlewWrapper");
        }

        WrapperPatchHelper.PatchLineNumbers range = managedRange.get();
        List<String> actualBlock = lines.subList(range.startIndex(), range.endIndex() + 1);

        if (!actualBlock.equals(expectedBlock)) {
            throw new ExceptionWithSuggestion("""
                Gradle Wrapper script is out of date: managed patches block does not match expected content.
                Please run `./gradlew patchGradlewWrapper` to fix.
                """, "./gradlew patchGradlewWrapper");
        }
    }

    /**
     * gradlew contains a comment block that explains how it works. We are trying to add the patch block after it.
     */
    private static int getInsertLineIndex(List<String> lines) {
        List<Integer> explanationBlock = IntStream.range(0, lines.size())
                .filter(i -> lines.get(i).startsWith(COMMENT_BLOCK))
                .limit(2)
                .boxed()
                .toList();
        if (explanationBlock.size() == 2 && explanationBlock.get(0) < explanationBlock.get(1)) {
            return explanationBlock.get(1) + 1;
        }

        throw new IllegalStateException("Unable to find where to patch the gradlew file, aborting...");
    }
}
