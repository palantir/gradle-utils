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
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.IntStream;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Resolves patch ordering using topological sort (Kahn's algorithm) over a Guava directed graph.
 * Ties are broken by registration order for determinism.
 */
final class PatchOrderResolver {

    private static final Logger log = Logging.getLogger(PatchOrderResolver.class);

    /**
     * Returns the given patches in an order consistent with their {@code mustRunAfter} and {@code mustRunBefore}
     * constraints. Patches without constraints are ordered by their registration order (list index).
     * References to unknown patch ids are logged as warnings and ignored.
     *
     * @throws IllegalStateException if a cycle is detected, duplicate ids/patchNames exist, or a reserved name is used
     */
    static List<PatchDeclaration> resolve(List<PatchDeclaration> patches) {
        if (patches.isEmpty()) {
            return List.of();
        }

        // id -> index; validates uniqueness and reserved names
        Map<String, Integer> idToIndex = validateAndIndexPatches(patches);

        // Build directed graph over indices; natural ordering gives registration-order tie-breaking
        MutableGraph<Integer> graph = buildGraph(patches, idToIndex);

        PriorityQueue<Integer> queue = new PriorityQueue<>();
        enqueueNodesWithNoInDegree(graph.nodes(), graph, queue);

        ImmutableList.Builder<PatchDeclaration> sorted = ImmutableList.builder();
        while (!queue.isEmpty()) {
            int current = queue.poll();
            sorted.add(patches.get(current));

            Set<Integer> successors = ImmutableSet.copyOf(graph.successors(current));
            graph.removeNode(current);
            enqueueNodesWithNoInDegree(successors, graph, queue);
        }

        // Any nodes remaining in the graph are cycle participants
        if (!graph.nodes().isEmpty()) {
            List<String> cycleNodes = graph.nodes().stream()
                    .map(node -> patches.get(node).getName())
                    .sorted()
                    .toList();
            throw new IllegalStateException(String.format(
                    "Cycle detected in patch ordering constraints involving patches: %s",
                    String.join(", ", cycleNodes)));
        }

        return sorted.build();
    }

    /** Validates patch declarations and returns a map of id to list index. */
    private static Map<String, Integer> validateAndIndexPatches(List<PatchDeclaration> patches) {
        Map<String, Integer> idToIndex = new HashMap<>();
        Set<String> seenPatchNames = new HashSet<>();
        for (int i = 0; i < patches.size(); i++) {
            String id = patches.get(i).getName();
            String patchName = patches.get(i).getPatchName().get();
            if (idToIndex.containsKey(id)) {
                throw new IllegalStateException(String.format("Duplicate patch id '%s'", id));
            }
            if (!seenPatchNames.add(patchName)) {
                throw new IllegalStateException(String.format("Duplicate patchName '%s'", patchName));
            }
            if (patchName.equals(WrapperPatchHelper.MANAGED_PATCH_NAME)) {
                throw new IllegalStateException(String.format(
                        "Patch '%s' uses reserved patchName '%s'", id, WrapperPatchHelper.MANAGED_PATCH_NAME));
            }
            idToIndex.put(id, i);
        }
        return idToIndex;
    }

    /** Builds a directed graph over list indices where an edge from i to j means patch i must come before patch j. */
    private static MutableGraph<Integer> buildGraph(List<PatchDeclaration> patches, Map<String, Integer> idToIndex) {
        MutableGraph<Integer> graph =
                GraphBuilder.directed().allowsSelfLoops(false).build();
        IntStream.range(0, patches.size()).forEach(graph::addNode);

        for (int i = 0; i < patches.size(); i++) {
            PatchDeclaration patch = patches.get(i);
            String id = patch.getName();

            for (String after : patch.getMustRunAfter().getOrElse(List.of())) {
                if (isKnownReference(after, idToIndex, id, "mustRunAfter")) {
                    graph.putEdge(idToIndex.get(after), i);
                }
            }

            for (String before : patch.getMustRunBefore().getOrElse(List.of())) {
                if (isKnownReference(before, idToIndex, id, "mustRunBefore")) {
                    graph.putEdge(i, idToIndex.get(before));
                }
            }
        }

        return graph;
    }

    /** Returns true if the referenced patch exists; logs a warning and returns false otherwise. */
    private static boolean isKnownReference(
            String referenced, Map<String, Integer> idToIndex, String source, String field) {
        if (idToIndex.containsKey(referenced)) {
            return true;
        }
        log.warn("Patch '{}' references unknown patch '{}' in {} — ignoring constraint", source, referenced, field);
        return false;
    }

    private static void enqueueNodesWithNoInDegree(
            Collection<Integer> nodes, MutableGraph<Integer> graph, Queue<Integer> queue) {
        nodes.stream().filter(node -> graph.inDegree(node) == 0).forEach(queue::add);
    }

    private PatchOrderResolver() {}
}
