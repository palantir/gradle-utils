/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.utils.develocityutils;

import com.gradle.develocity.agent.gradle.adapters.DevelocityAdapter;
import com.gradle.develocity.agent.gradle.adapters.develocity.DevelocityConfigurationAdapter;
import com.gradle.develocity.agent.gradle.adapters.enterprise.GradleEnterpriseExtensionAdapter;
import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public final class DevelocityUtils {

    private static final Logger log = Logging.getLogger(DevelocityUtils.class);

    private DevelocityUtils() {}

    public static Optional<DevelocityAdapter> getDevelocityExtension(Project project) {
        Object develocityConfiguration =
                project.getRootProject().getExtensions().findByName("develocity");
        if (develocityConfiguration != null) {
            return Optional.of(new DevelocityConfigurationAdapter(develocityConfiguration));
        }

        Object gradleEnterpriseExtension =
                project.getRootProject().getExtensions().findByName("gradleEnterprise");
        if (gradleEnterpriseExtension != null) {
            log.warn("Using deprecated gradle enterprise extension, please upgrade to the latest "
                    + "`com.palantir.build-scan.settings` settings plugin");
            return Optional.of(new GradleEnterpriseExtensionAdapter(gradleEnterpriseExtension));
        }

        return Optional.empty();
    }
}
