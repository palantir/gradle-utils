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

import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;

public abstract class WrapperPatcherTask extends DefaultTask {

    /** Patches in topologically sorted order. */
    @Nested
    public abstract ListProperty<OrderedPatch> getOrderedPatches();

    @InputFile
    public abstract RegularFileProperty getOriginalGradlewScript();

    /** Builds the full managed block from the ordered patches. */
    protected final List<String> buildManagedBlock() {
        List<String> patchLines = getOrderedPatches().get().stream()
                .flatMap(patch ->
                        WrapperPatchHelper.getPatchLinesWithHeader(
                                patch.getContent().get(), patch.getName().get())
                                .stream())
                .toList();
        return WrapperPatchHelper.wrapInManagedBlock(patchLines);
    }
}
