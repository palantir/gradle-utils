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

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.model.ObjectFactory;

/** Extension for declaring wrapper patches to be applied to the gradlew script. */
public abstract class WrapperPatcherExtension {

    private final ObjectFactory objectFactory;
    private final NamedDomainObjectList<PatchDeclaration> patches;

    @Inject
    public WrapperPatcherExtension(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        this.patches = objectFactory.namedDomainObjectList(PatchDeclaration.class);
    }

    public final NamedDomainObjectList<PatchDeclaration> getPatches() {
        return patches;
    }

    /** Convenience method to declare a patch with the given id and configure it via the action. */
    public PatchDeclaration patch(String id, Action<? super PatchDeclaration> action) {
        PatchDeclaration declaration = objectFactory.newInstance(PatchDeclaration.class, id);
        action.execute(declaration);
        patches.add(declaration);
        return declaration;
    }
}
