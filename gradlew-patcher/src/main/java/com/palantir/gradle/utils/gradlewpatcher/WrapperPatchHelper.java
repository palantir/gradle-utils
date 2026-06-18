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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility for managing marker-delimited patch blocks in shell scripts.
 * Patch blocks are identified by a header and footer comment line.
 */
final class WrapperPatchHelper {

    static final String MANAGED_PATCH_NAME = "Managed patches";

    static List<String> getLinesWithoutPatch(List<String> initialLines, String patchName) {
        Optional<PatchLineNumbers> patchLineRange = getPatchLineNumbers(initialLines, patchName);
        if (patchLineRange.isEmpty()) {
            return initialLines;
        }
        int startIndex = patchLineRange.get().startIndex();
        int endIndex = patchLineRange.get().endIndex();
        List<String> linesNoPatch = new ArrayList<>(initialLines.subList(0, startIndex));
        if (endIndex + 1 < initialLines.size()) {
            linesNoPatch.addAll(initialLines.subList(endIndex + 1, initialLines.size()));
        }
        return linesNoPatch;
    }

    // reads all lines including the trailing empty line (if the file ends with \n)
    static List<String> readAllLines(Path filePath) {
        try {
            return List.of(Files.readString(filePath).split("\n", -1));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read file: " + filePath, e);
        }
    }

    static void writeContentWithoutPatches(Path outputPath, List<String> lines) {
        writeContentWithPatch(outputPath, lines, List.of(), 0);
    }

    static void writeContentWithPatch(
            Path outputPath, List<String> initialLines, List<String> patchLines, int insertIndex) {
        try {
            Files.writeString(outputPath, getContentWithPatch(initialLines, patchLines, insertIndex));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write file: " + outputPath, e);
        }
    }

    static Optional<PatchLineNumbers> getPatchLineNumbers(List<String> content, String patchName) {
        String patchHeader = patchHeader(patchName);
        String patchFooter = patchFooter(patchName);
        List<Integer> startPatchIndexes = IntStream.range(0, content.size())
                .filter(i -> content.get(i).endsWith(patchHeader))
                .limit(2)
                .boxed()
                .toList();

        if (startPatchIndexes.size() > 1) {
            throw new IllegalArgumentException(String.format(
                    "Invalid patch, expected at most 1 header '%s', but got %s",
                    patchHeader, startPatchIndexes.size()));
        }

        if (startPatchIndexes.isEmpty()) {
            return Optional.empty();
        }

        int startIndex = startPatchIndexes.get(0);

        List<Integer> endPatchIndexes = IntStream.range(startIndex, content.size())
                .filter(i -> content.get(i).endsWith(patchFooter))
                .limit(2)
                .boxed()
                .toList();

        if (endPatchIndexes.size() > 1) {
            throw new IllegalArgumentException(String.format(
                    "Invalid patch, expected at most 1 footer '%s', but got %s", patchFooter, endPatchIndexes.size()));
        }

        if (endPatchIndexes.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "Invalid patch, found header '%s' but missing closing footer '%s'", patchHeader, patchFooter));
        }

        return Optional.of(new PatchLineNumbers(startIndex, endPatchIndexes.get(0)));
    }

    private static String getContentWithPatch(List<String> initialLines, List<String> patchLines, int insertIndex) {
        List<String> newLines = new ArrayList<>(initialLines.size() + patchLines.size());
        newLines.addAll(initialLines.subList(0, insertIndex));
        newLines.addAll(patchLines);
        newLines.addAll(initialLines.subList(insertIndex, initialLines.size()));
        return newLines.stream().collect(Collectors.joining("\n"));
    }

    static List<String> getLinesWithoutPatches(List<String> initialLines, List<String> patchNames) {
        // First try stripping the outer managed block
        List<String> lines = getLinesWithoutPatch(initialLines, MANAGED_PATCH_NAME);
        // Fall back to stripping individual patches for backwards compatibility
        if (lines.equals(initialLines)) {
            for (String patchName : patchNames) {
                lines = getLinesWithoutPatch(lines, patchName);
            }
        }
        return lines;
    }

    static List<String> wrapInManagedBlock(List<String> patchLines) {
        return wrapWithHeaderFooter(patchLines, MANAGED_PATCH_NAME);
    }

    static List<String> getPatchLinesWithHeader(String patchContent, String patchName) {
        return wrapWithHeaderFooter(patchContent.lines().toList(), patchName);
    }

    private static List<String> wrapWithHeaderFooter(List<String> contentLines, String patchName) {
        List<String> lines = new ArrayList<>(contentLines.size() + 2);
        lines.add(patchHeader(patchName));
        lines.addAll(contentLines);
        lines.add(patchFooter(patchName));
        return lines;
    }

    private static String patchHeader(String patchName) {
        return "# >>> " + patchName + " >>>";
    }

    private static String patchFooter(String patchName) {
        return "# <<< " + patchName + " <<<";
    }

    record PatchLineNumbers(int startIndex, int endIndex) {}

    private WrapperPatchHelper() {}
}
