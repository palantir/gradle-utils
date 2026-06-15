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

import java.util.List;
import org.gradle.api.model.ObjectFactory;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatchOrderResolverTest {

    private ObjectFactory objects;

    @BeforeEach
    void before() {
        objects = ProjectBuilder.builder().build().getObjects();
    }

    @Test
    void empty_list() {
        assertThat(PatchOrderResolver.resolve(List.of())).isEmpty();
    }

    @Test
    void single_patch() {
        PatchDeclaration patchDeclaration = patch("A");
        assertThat(resolveIds(List.of(patchDeclaration))).containsExactly("A");
    }

    @Test
    void no_constraints_preserves_registration_order() {
        PatchDeclaration patchA = patch("A");
        PatchDeclaration patchB = patch("B");
        PatchDeclaration patchC = patch("C");

        assertThat(resolveIds(List.of(patchA, patchB, patchC))).containsExactly("A", "B", "C");
    }

    @Test
    void must_run_before() {
        PatchDeclaration patchA = patch("A");
        PatchDeclaration patchB = patch("B");
        patchB.getMustRunBefore().set(List.of("A"));

        assertThat(resolveIds(List.of(patchA, patchB))).containsExactly("B", "A");
    }

    @Test
    void must_run_after() {
        PatchDeclaration patchA = patch("A");
        PatchDeclaration patchB = patch("B");
        patchA.getMustRunAfter().set(List.of("B"));

        assertThat(resolveIds(List.of(patchA, patchB))).containsExactly("B", "A");
    }

    @Test
    void chain_ordering() {
        PatchDeclaration patchA = patch("A");
        PatchDeclaration patchB = patch("B");
        PatchDeclaration patchC = patch("C");
        patchA.getMustRunBefore().set(List.of("B"));
        patchB.getMustRunBefore().set(List.of("C"));

        assertThat(resolveIds(List.of(patchC, patchB, patchA))).containsExactly("A", "B", "C");
    }

    @Test
    void unconstrained_patches_ordered_by_registration() {
        PatchDeclaration patchA = patch("A");
        PatchDeclaration patchB = patch("B");
        PatchDeclaration patchC = patch("C");
        patchC.getMustRunBefore().set(List.of("A"));

        // C must be before A (edge C→A, so A has in-degree 1).
        // Initially B (index 1) and C (index 2) have in-degree 0. Tie-break: B first.
        // After B: C emitted. After C: A emitted.
        assertThat(resolveIds(List.of(patchA, patchB, patchC))).containsExactly("B", "C", "A");
    }

    @Test
    void cycle_throws() {
        PatchDeclaration patchA = patch("A");
        PatchDeclaration patchB = patch("B");
        patchA.getMustRunAfter().set(List.of("B"));
        patchB.getMustRunAfter().set(List.of("A"));

        assertThatThrownBy(() -> PatchOrderResolver.resolve(List.of(patchA, patchB)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cycle detected")
                .hasMessageContaining("A")
                .hasMessageContaining("B");
    }

    @Test
    void unknown_reference_in_must_run_after_throws() {
        PatchDeclaration patchA = patch("A");
        patchA.getMustRunAfter().set(List.of("nonexistent"));

        assertThatThrownBy(() -> PatchOrderResolver.resolve(List.of(patchA)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown patch 'nonexistent'");
    }

    @Test
    void unknown_reference_in_must_run_before_throws() {
        PatchDeclaration patchA = patch("A");
        patchA.getMustRunBefore().set(List.of("nonexistent"));

        assertThatThrownBy(() -> PatchOrderResolver.resolve(List.of(patchA)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown patch 'nonexistent'");
    }

    @Test
    void duplicate_patch_name_throws() {
        PatchDeclaration a1 = patch("A");
        PatchDeclaration a2 = patch("A");

        assertThatThrownBy(() -> PatchOrderResolver.resolve(List.of(a1, a2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate patch id 'A'");
    }

    private PatchDeclaration patch(String id) {
        PatchDeclaration patchDeclaration = objects.newInstance(PatchDeclaration.class);
        patchDeclaration.getId().set(id);
        patchDeclaration.getPatchName().set(id);
        patchDeclaration.getContent().set("echo " + id);
        return patchDeclaration;
    }

    private List<String> resolveIds(List<PatchDeclaration> patches) {
        return PatchOrderResolver.resolve(patches).stream()
                .map(p -> p.getId().get())
                .toList();
    }
}
