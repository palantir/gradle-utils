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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Validates that all registered patches are present in the gradlew wrapper script with the expected content.
 */
public abstract class CheckGradlewTask extends WrapperPatcherTask {

    /**
     * Marker file written on successful validation. This task only reads the gradlew script and has no natural output,
     * so without a declared output Gradle cannot track up-to-date state and would re-run the task every time.
     */
    @OutputFile
    public abstract RegularFileProperty getStampFile();

    @TaskAction
    public final void action() {
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
            touchStampFile();
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

        touchStampFile();
    }

    private void touchStampFile() {
        try {
            Files.writeString(getStampFile().getAsFile().get().toPath(), "");
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write stamp file", e);
        }
    }
}
