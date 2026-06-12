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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
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
    private static final String SHEBANG = "#!";

    /** Patch names in topologically sorted order. */
    @Input
    public abstract ListProperty<String> getOrderedPatchNames();

    /** Mapping from patch name to patch content (without header/footer markers). */
    @Input
    public abstract MapProperty<String, String> getPatchContents();

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
        List<String> patchNames = getOrderedPatchNames().get();
        Map<String, String> contents = getPatchContents().get();

        File originalGradlewScript = getOriginalGradlewScript().getAsFile().get();
        List<String> lines = WrapperPatchHelper.readAllLines(originalGradlewScript.toPath());

        // Strip all existing patches
        lines = WrapperPatchHelper.getLinesWithoutPatches(lines, patchNames);

        // Find insertion point
        int insertIndex = getInsertLineIndex(lines);

        List<String> allPatchLines = patchNames.stream()
                .flatMap(name -> WrapperPatchHelper.getPatchLinesWithHeader(contents.get(name), name).stream())
                .toList();

        WrapperPatchHelper.writeContentWithPatch(
                getPatchedGradlewScript().getAsFile().get().toPath(), lines, allPatchLines, insertIndex);
    }

    private void checkContainsPatches() {
        List<String> patchNames = getOrderedPatchNames().get();
        Map<String, String> contents = getPatchContents().get();

        File gradlewFile = getOriginalGradlewScript().get().getAsFile();
        List<String> lines = WrapperPatchHelper.readAllLines(gradlewFile.toPath());

        List<String> errors = new ArrayList<>();
        int lastEndIndex = -1;

        for (String name : patchNames) {
            List<String> expectedLines = WrapperPatchHelper.getPatchLinesWithHeader(contents.get(name), name);
            Optional<WrapperPatchHelper.PatchLineNumbers> lineNumbers =
                    WrapperPatchHelper.getPatchLineNumbers(lines, name);

            if (lineNumbers.isEmpty()) {
                errors.add(String.format("Patch '%s' is missing", name));
                continue;
            }

            WrapperPatchHelper.PatchLineNumbers patchLines = lineNumbers.get();
            List<String> actualLines = lines.subList(patchLines.startIndex(), patchLines.endIndex() + 1);

            if (!actualLines.equals(expectedLines)) {
                errors.add(String.format("Patch '%s' content does not match expected", name));
            }

            if (patchLines.startIndex() <= lastEndIndex) {
                errors.add(String.format("Patch '%s' is out of order", name));
            }

            lastEndIndex = patchLines.endIndex();
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("""
                Gradle Wrapper script is out of date:
                  - %s
                Please run `./gradlew patchGradlewWrapper` to fix.
                """.formatted(String.join("\n  - ", errors)));
        }
    }

    /**
     * gradlew contains a comment block that explains how it works. We are trying to add the patch block after it.
     * The fallback is adding the patch block directly after the shebang line.
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

        int shebangLine = lines.indexOf(SHEBANG);
        if (shebangLine != -1) {
            return shebangLine + 1;
        }

        throw new IllegalStateException("Unable to find where to patch the gradlew file, aborting...");
    }
}
