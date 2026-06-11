/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

            Optional<PatchLineNumbers> result = WrapperPatchHelper.getPatchLineNumbers(lines, HEADER, FOOTER);

            assertThat(result).isPresent();
            assertThat(result.get().startIndex()).isEqualTo(1);
            assertThat(result.get().endIndex()).isEqualTo(3);
        }

        @Test
        void returns_empty_when_no_patch() {
            List<String> lines = List.of("before", "content", "after");

            assertThat(WrapperPatchHelper.getPatchLineNumbers(lines, HEADER, FOOTER)).isEmpty();
        }

        @Test
        void throws_on_duplicate_headers() {
            List<String> lines = List.of(HEADER, "content", FOOTER, HEADER, "more", FOOTER);

            assertThatThrownBy(() -> WrapperPatchHelper.getPatchLineNumbers(lines, HEADER, FOOTER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expected at most 1 header");
        }

        @Test
        void throws_on_missing_footer() {
            List<String> lines = List.of(HEADER, "content", "no footer");

            assertThatThrownBy(() -> WrapperPatchHelper.getPatchLineNumbers(lines, HEADER, FOOTER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing closing footer");
        }
    }

    @Nested
    class GetLinesWithoutPatch {
        @Test
        void removes_existing_patch() {
            List<String> lines = List.of("before", HEADER, "content", FOOTER, "after");

            List<String> result = WrapperPatchHelper.getLinesWithoutPatch(lines, HEADER, FOOTER);

            assertThat(result).containsExactly("before", "after");
        }

        @Test
        void returns_all_lines_when_no_patch() {
            List<String> lines = List.of("a", "b", "c");

            List<String> result = WrapperPatchHelper.getLinesWithoutPatch(lines, HEADER, FOOTER);

            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        void handles_patch_at_end_of_file() {
            List<String> lines = List.of("before", HEADER, "content", FOOTER);

            List<String> result = WrapperPatchHelper.getLinesWithoutPatch(lines, HEADER, FOOTER);

            assertThat(result).containsExactly("before");
        }

        @Test
        void handles_patch_at_start_of_file() {
            List<String> lines = List.of(HEADER, "content", FOOTER, "after");

            List<String> result = WrapperPatchHelper.getLinesWithoutPatch(lines, HEADER, FOOTER);

            assertThat(result).containsExactly("after");
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

            List<String> lines = List.of("before", headerA, "a-content", footerA, headerB, "b-content", footerB, "after");

            // Can find patch A
            Optional<PatchLineNumbers> patchA = WrapperPatchHelper.getPatchLineNumbers(lines, headerA, footerA);
            assertThat(patchA).isPresent();
            assertThat(patchA.get().startIndex()).isEqualTo(1);
            assertThat(patchA.get().endIndex()).isEqualTo(3);

            // Can find patch B
            Optional<PatchLineNumbers> patchB = WrapperPatchHelper.getPatchLineNumbers(lines, headerB, footerB);
            assertThat(patchB).isPresent();
            assertThat(patchB.get().startIndex()).isEqualTo(4);
            assertThat(patchB.get().endIndex()).isEqualTo(6);

            // Can remove patch A, leaving patch B
            List<String> withoutA = WrapperPatchHelper.getLinesWithoutPatch(lines, headerA, footerA);
            assertThat(withoutA).containsExactly("before", headerB, "b-content", footerB, "after");

            // Can remove patch B, leaving patch A
            List<String> withoutB = WrapperPatchHelper.getLinesWithoutPatch(lines, headerB, footerB);
            assertThat(withoutB).containsExactly("before", headerA, "a-content", footerA, "after");
        }
    }
}
