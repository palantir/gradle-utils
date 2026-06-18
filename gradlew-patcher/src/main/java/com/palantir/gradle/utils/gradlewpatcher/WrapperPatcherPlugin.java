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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/** Registers composite patch and check tasks for the gradlew wrapper script. */
public abstract class WrapperPatcherPlugin implements Plugin<Project> {

    @Override
    public final void apply(Project project) {
        if (project.getRootProject() != project) {
            throw new IllegalArgumentException("com.palantir.gradlew-patcher must be applied to the root project only");
        }
        @SuppressWarnings("for-rollout:GradleTypesAsFields")
        WrapperPatcherExtension extension =
                project.getExtensions().create("wrapperPatches", WrapperPatcherExtension.class);

        TaskProvider<Wrapper> wrapperTask = project.getTasks().named("wrapper", Wrapper.class);

        // Configure shared inputs for all WrapperPatcherTask subtypes
        ObjectFactory objects = project.getObjects();
        project.getTasks().withType(WrapperPatcherTask.class).configureEach(task -> {
            task.getOrderedPatches()
                    .set(project.provider(() -> PatchOrderResolver.resolve(extension.getPatches()).stream()
                            .map(patch -> {
                                OrderedPatch ordered = objects.newInstance(OrderedPatch.class);
                                ordered.getName().set(patch.getPatchName().get());
                                ordered.getContent().set(patch.getContent().get());
                                return ordered;
                            })
                            .toList()));
            // wrapped in provider to avoid an implicit task dependency on wrapperTask
            task.getOriginalGradlewScript()
                    .fileProvider(project.provider(() -> wrapperTask.get().getScriptFile()));
        });

        TaskProvider<PatchGradlewTask> patchTask = project.getTasks()
                .register("patchGradlewWrapper", PatchGradlewTask.class, task -> {
                    task.getPatchedGradlewScript()
                            .set(project.file(project.getRootDir().toPath().resolve("gradlew")));
                });

        TaskProvider<CheckGradlewTask> checkTask = project.getTasks()
                .register("checkGradlewWrapper", CheckGradlewTask.class, task -> {
                    task.getStampFile()
                            .set(project.getLayout().getBuildDirectory().file("checkGradlewWrapper.stamp"));
                });

        wrapperTask.configure(task -> task.finalizedBy(patchTask));

        project.getPluginManager().withPlugin("lifecycle-base", _plugin -> {
            project.getTasks()
                    .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                    .configure(check -> check.dependsOn(checkTask));
        });
    }
}
