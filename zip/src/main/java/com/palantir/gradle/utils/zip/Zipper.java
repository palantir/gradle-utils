/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.utils.zip;

import org.gradle.api.provider.Provider;

/**
 * Placeholder Zipper class for compilation before annotation processing.
 * The annotation processor will generate the real implementation with proper zip methods.
 *
 * This class exists to:
 * 1. Allow code to compile that references Zipper before annotation processing
 * 2. Provide IDE completion hints about the intended API
 * 3. Document the expected behavior of generated methods
 */
public class Zipper {

    /**
     * Placeholder zip method.
     */
    public Provider<?> zip(Provider<?>... _providers) {
        throw new UnsupportedOperationException(
                "This is a placeholder Zipper class. The real implementation should be generated "
                        + "by the annotation processor. Ensure the annotation processor is configured "
                        + "correctly and that @Zips annotations are present.");
    }
}
