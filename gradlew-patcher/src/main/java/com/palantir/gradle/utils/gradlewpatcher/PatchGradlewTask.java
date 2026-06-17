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

import java.io.File;
import java.util.List;
import java.util.stream.IntStream;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Strips all existing patches from the gradlew wrapper script and re-inserts the registered patches
 * in topological order, wrapped in a managed block.
 */
public abstract class PatchGradlewTask extends WrapperPatcherTask {

    private static final Logger log = Logging.getLogger(PatchGradlewTask.class);
    private static final String COMMENT_BLOCK = "###";

    @OutputFile
    public abstract RegularFileProperty getPatchedGradlewScript();

    @TaskAction
    public final void action() {
        log.lifecycle("Patching the gradle wrapper files.");

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

        List<String> managedBlock = buildManagedBlock();

        WrapperPatchHelper.writeContentWithPatch(
                getPatchedGradlewScript().getAsFile().get().toPath(), lines, managedBlock, insertIndex);
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
