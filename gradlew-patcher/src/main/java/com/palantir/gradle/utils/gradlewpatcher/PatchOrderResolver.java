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

import com.google.common.collect.ImmutableList;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Resolves patch ordering using topological sort (Kahn's algorithm) over a Guava directed graph.
 * Ties are broken by registration order for determinism.
 */
final class PatchOrderResolver {

    /**
     * Returns the given patches in an order consistent with their {@code mustRunAfter} and {@code mustRunBefore}
     * constraints. Patches without constraints are ordered by their registration order (list index).
     *
     * @throws IllegalStateException if a cycle is detected or an unknown patch id is referenced
     */
    static List<PatchDeclaration> resolve(List<PatchDeclaration> patches) {
        if (patches.isEmpty()) {
            return List.of();
        }

        Map<String, PatchDeclaration> byId = new LinkedHashMap<>();
        for (PatchDeclaration patch : patches) {
            String id = patch.getId().get();
            String patchName = patch.getPatchName().get();
            if (byId.containsKey(id)) {
                throw new IllegalStateException(String.format("Duplicate patch id '%s'", id));
            }
            if (patchName.equals(WrapperPatchHelper.MANAGED_PATCH_NAME)) {
                throw new IllegalStateException(String.format(
                        "Patch '%s' uses reserved patchName '%s'", id, WrapperPatchHelper.MANAGED_PATCH_NAME));
            }
            byId.put(id, patch);
        }
        Graph<String> graph = buildGraph(patches, byId.keySet());

        Map<String, Integer> inDegree = new HashMap<>();
        graph.nodes()
                .forEach(node -> inDegree.put(node, graph.predecessors(node).size()));

        // Build a registration-order index for deterministic tie-breaking
        Map<String, Integer> registrationOrder = IntStream.range(0, patches.size())
                .boxed()
                .collect(Collectors.toMap(i -> patches.get(i).getId().get(), i -> i));
        Comparator<String> byRegistrationOrder = Comparator.comparingInt(registrationOrder::get);

        Queue<String> queue = enqueueRootNodes(inDegree, byRegistrationOrder);
        ImmutableList.Builder<PatchDeclaration> sorted = ImmutableList.builder();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(byId.get(current));

            for (String successor : graph.successors(current)) {
                int newDegree = inDegree.merge(successor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(successor);
                }
            }
        }

        List<PatchDeclaration> sortedPatches = sorted.build();
        if (sortedPatches.size() != patches.size()) {
            List<String> cycleNodes = inDegree.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            throw new IllegalStateException(String.format(
                    "Cycle detected in patch ordering constraints involving: %s", String.join(", ", cycleNodes)));
        }

        return sortedPatches;
    }

    /** Builds a directed graph where an edge from A to B means A must come before B. */
    private static Graph<String> buildGraph(List<PatchDeclaration> patches, Set<String> knownIds) {
        MutableGraph<String> graph =
                GraphBuilder.directed().allowsSelfLoops(false).build();
        knownIds.forEach(graph::addNode);

        for (PatchDeclaration patch : patches) {
            String id = patch.getId().get();

            for (String after : patch.getMustRunAfter().getOrElse(List.of())) {
                validateReference(after, knownIds, id, "mustRunAfter");
                graph.putEdge(after, id);
            }

            for (String before : patch.getMustRunBefore().getOrElse(List.of())) {
                validateReference(before, knownIds, id, "mustRunBefore");
                graph.putEdge(id, before);
            }
        }

        return graph;
    }

    private static Queue<String> enqueueRootNodes(Map<String, Integer> degreeByNode, Comparator<String> tieBreaker) {
        Queue<String> queue = new PriorityQueue<>(tieBreaker);
        degreeByNode.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .forEach(entry -> queue.add(entry.getKey()));
        return queue;
    }

    private static void validateReference(String referenced, Set<String> knownIds, String source, String field) {
        if (!knownIds.contains(referenced)) {
            throw new IllegalStateException(
                    String.format("Patch '%s' references unknown patch '%s' in %s", source, referenced, field));
        }
    }

    private PatchOrderResolver() {}
}
