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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Gradle task that patches or validates a patch block in the gradlew wrapper script.
 *
 * <p>When {@code generate} is {@code true}, the task inserts (or updates) the patch block.
 * When {@code false}, the task validates the existing patch matches the expected content.
 */
public abstract class WrapperPatcherTask extends DefaultTask {

    private static final Logger log = Logging.getLogger(WrapperPatcherTask.class);
    private static final String COMMENT_BLOCK = "###";
    private static final String SHEBANG = "#!";

    @Input
    public abstract Property<String> getPatchHeader();

    @Input
    public abstract Property<String> getPatchFooter();

    @Input
    public abstract Property<String> getPatchResource();

    @Input
    public abstract Property<Boolean> getGenerate();

    @InputFile
    public abstract RegularFileProperty getOriginalGradlewScript();

    @OutputFile
    public abstract RegularFileProperty getPatchedGradlewScript();

    @Internal
    public abstract RegularFileProperty getBuildDir();

    @Input
    public abstract Property<String> getPatchTaskName();

    public WrapperPatcherTask() {
        getGenerate().convention(false);
    }

    @TaskAction
    public final void action() {
        if (getGenerate().get()) {
            patchGradlewContent();
        } else {
            checkContainsPatch();
        }
    }

    private void checkContainsPatch() {
        List<String> scriptPatchLines = getPatchedLines();
        List<String> expectedPatchLines = loadPatchLines();
        if (!scriptPatchLines.equals(expectedPatchLines)) {
            throw new IllegalStateException("Gradle Wrapper script is out of date, please run `./gradlew "
                    + getPatchTaskName().get() + "` to fix.");
        }
    }

    private void patchGradlewContent() {
        File originalGradlewScript = getOriginalGradlewScript().getAsFile().get();
        List<String> initialLines = WrapperPatchHelper.readAllLines(originalGradlewScript.toPath());
        List<String> linesNoPatch = WrapperPatchHelper.getLinesWithoutPatch(
                initialLines, getPatchHeader().get(), getPatchFooter().get());
        List<String> patchLines = loadPatchLines();
        int insertIndex = getInsertLineIndex(linesNoPatch);
        WrapperPatchHelper.writeContentWithPatch(
                getPatchedGradlewScript().getAsFile().get().toPath(), linesNoPatch, patchLines, insertIndex);
    }

    private List<String> getPatchedLines() {
        File gradlewFile = getOriginalGradlewScript().get().getAsFile();
        List<String> initialLines = WrapperPatchHelper.readAllLines(gradlewFile.toPath());
        return WrapperPatchHelper.getPatchLineNumbers(
                        initialLines, getPatchHeader().get(), getPatchFooter().get())
                .map(patchLineNumbers ->
                        initialLines.subList(patchLineNumbers.startIndex(), patchLineNumbers.endIndex() + 1))
                .orElseGet(List::of);
    }

    /**
     * Determines where to insert the patch block. Priority:
     * <ol>
     *   <li>After the {@code ###} explanation comment block</li>
     *   <li>After the shebang line</li>
     * </ol>
     */
    private int getInsertLineIndex(List<String> lines) {
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

    private List<String> loadPatchLines() {
        String resource = getPatchResource().get();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resource);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Unable to read the %s patch file", resource), e);
        }
    }
}
