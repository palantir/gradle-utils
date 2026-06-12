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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves patch ordering using topological sort (Kahn's algorithm).
 * Ties are broken by registration order for determinism.
 */
final class PatchOrderResolver {

    /**
     * Returns the given patches in an order consistent with their {@code mustRunAfter} and {@code mustRunBefore}
     * constraints. Patches without constraints are ordered by their registration order (list index).
     *
     * @throws IllegalStateException if a cycle is detected or an unknown patch name is referenced
     */
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    static List<PatchDeclaration> resolve(List<PatchDeclaration> patches) {
        if (patches.isEmpty()) {
            return List.of();
        }

        // Map patchName -> declaration, preserving insertion order for tie-breaking
        Map<String, PatchDeclaration> byName = new LinkedHashMap<>();
        Map<String, Integer> registrationIndex = new HashMap<>();
        for (int i = 0; i < patches.size(); i++) {
            PatchDeclaration patch = patches.get(i);
            String name = patch.getId().get();
            if (byName.containsKey(name)) {
                throw new IllegalStateException(String.format("Duplicate patch id '%s'", name));
            }
            byName.put(name, patch);
            registrationIndex.put(name, i);
        }

        Set<String> knownNames = byName.keySet();

        // Build adjacency list and in-degree map
        // Edge from A -> B means A must come before B
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String name : knownNames) {
            adjacency.put(name, new ArrayList<>());
            inDegree.put(name, 0);
        }

        for (PatchDeclaration patch : patches) {
            String name = patch.getId().get();

            // mustRunAfter: this patch runs after the listed patches → edge from each listed → this
            for (String after : patch.getMustRunAfter().getOrElse(List.of())) {
                validateReference(after, knownNames, name, "mustRunAfter");
                adjacency.get(after).add(name);
                inDegree.merge(name, 1, Integer::sum);
            }

            // mustRunBefore: this patch runs before the listed patches → edge from this → each listed
            for (String before : patch.getMustRunBefore().getOrElse(List.of())) {
                validateReference(before, knownNames, name, "mustRunBefore");
                adjacency.get(name).add(before);
                inDegree.merge(before, 1, Integer::sum);
            }
        }

        // Kahn's algorithm with priority queue for deterministic tie-breaking (by registration order)
        Queue<String> queue = new PriorityQueue<>(Comparator.comparingInt(registrationIndex::get));
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<PatchDeclaration> sorted = new ArrayList<>(patches.size());
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(byName.get(current));

            for (String neighbor : adjacency.get(current)) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sorted.size() != patches.size()) {
            List<String> cycleNodes = inDegree.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            throw new IllegalStateException(String.format(
                    "Cycle detected in patch ordering constraints involving: %s",
                    cycleNodes.stream().collect(Collectors.joining(", "))));
        }

        return sorted;
    }

    private static void validateReference(String referenced, Set<String> knownNames, String source, String field) {
        if (!knownNames.contains(referenced)) {
            throw new IllegalStateException(
                    String.format("Patch '%s' references unknown patch '%s' in %s", source, referenced, field));
        }
    }

    private PatchOrderResolver() {}
}
