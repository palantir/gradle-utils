/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.utils.dependencygraph;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Attribute;

public final class DependencyGraphUtils {
    public static Set<ResolvedComponentResult> allComponentResultsFromRoot(ResolvedComponentResult rootResult) {
        Set<ResolvedComponentResult> seen = new HashSet<>();
        Queue<ResolvedComponentResult> next = new ArrayDeque<>();

        next.add(rootResult);

        ResolvedComponentResult current;
        while ((current = next.poll()) != null) {
            // GCV makes a project dependency on the root project for a "platform" of version constraints. This exists
            // in every configuration that GCV inserts constraints into and is not a mavenBundle dep. However, there
            // can be a real mavenBundle dep as well that presents itself as another variant. So we must reject
            // component results that have just the GCV variant but not if there is another variant too.
            boolean onlySelectedForGcvProps = current.getVariants().stream()
                    .allMatch(resolvedVariantResult -> Optional.ofNullable(resolvedVariantResult
                                    .getAttributes()
                                    .getAttribute(Attribute.of("org.gradle.usage", String.class)))
                            .equals(Optional.of("consistent-versions-usage")));

            if (onlySelectedForGcvProps) {
                continue;
            }

            seen.add(current);

            current.getDependencies().forEach(dependencyResult -> {
                if (dependencyResult instanceof UnresolvedDependencyResult) {
                    throw new RuntimeException(
                            "Failed to resolve "
                                    + dependencyResult.getRequested().getDisplayName(),
                            ((UnresolvedDependencyResult) dependencyResult).getFailure());
                }

                ResolvedDependencyResult resolvedDependencyResult = (ResolvedDependencyResult) dependencyResult;
                ResolvedComponentResult resolvedComponentResult = resolvedDependencyResult.getSelected();

                if (!seen.contains(resolvedComponentResult)) {
                    next.add(resolvedComponentResult);
                }
            });
        }

        return seen;
    }

    private DependencyGraphUtils() {}
}
