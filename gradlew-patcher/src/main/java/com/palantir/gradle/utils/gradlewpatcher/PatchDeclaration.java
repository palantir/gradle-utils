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

import org.gradle.api.Named;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/** Per-patch configuration declared via the {@link WrapperPatcherExtension} DSL. */
public interface PatchDeclaration extends Named {

    /** Unique identifier for this patch, used in ordering constraints ({@code mustRunAfter}/{@code mustRunBefore}). */
    @Override
    String getName();

    /** Human-readable name used for header/footer markers in the gradlew script. */
    Property<String> getPatchName();

    /** Shell script lines to insert between the header and footer markers. */
    Property<String> getContent();

    /**
     * Patch IDs that this patch must run after. Useful when this patch depends on another patch being applied first.
     * References to unknown IDs are ignored with a warning.
     */
    ListProperty<String> getMustRunAfter();

    /**
     * Patch IDs that this patch must run before. Useful when another patch depends on this one being applied first.
     * References to unknown IDs are ignored with a warning.
     */
    ListProperty<String> getMustRunBefore();
}
