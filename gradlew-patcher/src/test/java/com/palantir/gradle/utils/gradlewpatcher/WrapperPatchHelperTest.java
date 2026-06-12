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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WrapperPatchHelperTest {

    private static final String PATCH_NAME = "Test patch";
    private static final String HEADER = "# >>> Test patch >>>";
    private static final String FOOTER = "# <<< Test patch <<<";

    @TempDir
    Path tmpDir;

    @Nested
    class WriteContentWithPatch {
        @Test
        void correctly_adds_patch() throws IOException {
            List<String> original = List.of("a", "b", "c", "d", "e", "f", "g");
            List<String> patch = List.of(HEADER, "content", FOOTER);
            Path processedFile = tmpDir.resolve("result.txt");

            WrapperPatchHelper.writeContentWithPatch(processedFile, original, patch, 4);

            assertThat(Files.readAllLines(processedFile))
                    .containsExactly("a", "b", "c", "d", HEADER, "content", FOOTER, "e", "f", "g");
        }
    }

    @Nested
    class GetPatchLineNumbers {
        @Test
        void finds_patch_block() {
            List<String> lines = List.of("before", HEADER, "content", FOOTER, "after");

            Optional<WrapperPatchHelper.PatchLineNumbers> result =
                    WrapperPatchHelper.getPatchLineNumbers(lines, PATCH_NAME);

            assertThat(result).isPresent();
            assertThat(result.get().startIndex()).isEqualTo(1);
            assertThat(result.get().endIndex()).isEqualTo(3);
        }

        @Test
        void returns_empty_when_no_patch() {
            List<String> lines = List.of("before", "content", "after");

            assertThat(WrapperPatchHelper.getPatchLineNumbers(lines, PATCH_NAME))
                    .isEmpty();
        }

        @Test
        void throws_on_duplicate_headers() {
            List<String> lines = List.of(HEADER, "content", FOOTER, HEADER, "more", FOOTER);

            assertThatThrownBy(() -> WrapperPatchHelper.getPatchLineNumbers(lines, PATCH_NAME))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expected at most 1 header");
        }

        @Test
        void throws_on_missing_footer() {
            List<String> lines = List.of(HEADER, "content", "no footer");

            assertThatThrownBy(() -> WrapperPatchHelper.getPatchLineNumbers(lines, PATCH_NAME))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing closing footer");
        }
    }

    @Nested
    class GetLinesWithoutPatch {
        @Test
        void removes_existing_patch() {
            List<String> lines = List.of("before", HEADER, "content", FOOTER, "after");

            List<String> result = WrapperPatchHelper.getLinesWithoutPatch(lines, PATCH_NAME);

            assertThat(result).containsExactly("before", "after");
        }

        @Test
        void returns_all_lines_when_no_patch() {
            List<String> lines = List.of("a", "b", "c");

            List<String> result = WrapperPatchHelper.getLinesWithoutPatch(lines, PATCH_NAME);

            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        void handles_patch_at_end_of_file() {
            List<String> lines = List.of("before", HEADER, "content", FOOTER);

            List<String> result = WrapperPatchHelper.getLinesWithoutPatch(lines, PATCH_NAME);

            assertThat(result).containsExactly("before");
        }

        @Test
        void handles_patch_at_start_of_file() {
            List<String> lines = List.of(HEADER, "content", FOOTER, "after");

            List<String> result = WrapperPatchHelper.getLinesWithoutPatch(lines, PATCH_NAME);

            assertThat(result).containsExactly("after");
        }
    }

    @Nested
    class ReadAllLines {
        @Test
        void preserves_trailing_newline(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("trailing.txt");
            Files.writeString(file, "a\nb\n");

            assertThat(WrapperPatchHelper.readAllLines(file)).containsExactly("a", "b", "");
        }

        @Test
        void handles_no_trailing_newline(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("no-trailing.txt");
            Files.writeString(file, "a\nb");

            assertThat(WrapperPatchHelper.readAllLines(file)).containsExactly("a", "b");
        }
    }

    @Nested
    class DifferentPatchHeaders {
        @Test
        void two_patches_with_different_markers_coexist() {
            String headerA = "# >>> Patch A >>>";
            String footerA = "# <<< Patch A <<<";
            String headerB = "# >>> Patch B >>>";
            String footerB = "# <<< Patch B <<<";

            List<String> lines =
                    List.of("before", headerA, "a-content", footerA, headerB, "b-content", footerB, "after");

            // Can find patch A
            Optional<WrapperPatchHelper.PatchLineNumbers> patchA =
                    WrapperPatchHelper.getPatchLineNumbers(lines, "Patch A");
            assertThat(patchA).isPresent();
            assertThat(patchA.get().startIndex()).isEqualTo(1);
            assertThat(patchA.get().endIndex()).isEqualTo(3);

            // Can find patch B
            Optional<WrapperPatchHelper.PatchLineNumbers> patchB =
                    WrapperPatchHelper.getPatchLineNumbers(lines, "Patch B");
            assertThat(patchB).isPresent();
            assertThat(patchB.get().startIndex()).isEqualTo(4);
            assertThat(patchB.get().endIndex()).isEqualTo(6);

            // Can remove patch A, leaving patch B
            List<String> withoutA = WrapperPatchHelper.getLinesWithoutPatch(lines, "Patch A");
            assertThat(withoutA).containsExactly("before", headerB, "b-content", footerB, "after");

            // Can remove patch B, leaving patch A
            List<String> withoutB = WrapperPatchHelper.getLinesWithoutPatch(lines, "Patch B");
            assertThat(withoutB).containsExactly("before", headerA, "a-content", footerA, "after");
        }
    }
}
