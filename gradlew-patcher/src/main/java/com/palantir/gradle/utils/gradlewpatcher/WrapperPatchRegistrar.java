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

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Registers wrapper patcher tasks and wires them into the Gradle lifecycle.
 *
 * <p>This sets up:
 * <ul>
 *   <li>A patch task (runs after {@code wrapper} task via {@code finalizedBy})</li>
 *   <li>A check task (runs during {@code check} lifecycle if {@code lifecycle-base} is applied)</li>
 *   <li>Common configuration for both (input/output gradlew files)</li>
 * </ul>
 */
public final class WrapperPatchRegistrar {

    /**
     * Registers patch and check tasks for the given config and wires them into the Gradle lifecycle.
     * Returns the task providers so the consumer can add dependencies (e.g. generation tasks).
     */
    public static WrapperPatchRegistration register(Project rootProject, WrapperPatchConfig config) {
        TaskProvider<Wrapper> wrapperTask = rootProject.getTasks().named("wrapper", Wrapper.class);

        TaskProvider<WrapperPatcherTask> patchTask = rootProject
                .getTasks()
                .register(config.patchTaskName(), WrapperPatcherTask.class, task -> {
                    task.getGenerate().set(true);
                });

        TaskProvider<WrapperPatcherTask> checkTask = rootProject
                .getTasks()
                .register(config.checkTaskName(), WrapperPatcherTask.class, task -> {
                    task.getGenerate().set(false);
                });

        // Common configuration for all WrapperPatcherTask instances registered by this config
        configureTask(rootProject, patchTask, wrapperTask, config);
        configureTask(rootProject, checkTask, wrapperTask, config);

        wrapperTask.configure(task -> {
            task.finalizedBy(patchTask);
        });

        rootProject.getPluginManager().withPlugin("lifecycle-base", _plugin -> {
            rootProject
                    .getTasks()
                    .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                    .configure(check -> check.dependsOn(checkTask));
        });

        return new WrapperPatchRegistration(patchTask, checkTask);
    }

    private static void configureTask(
            Project rootProject,
            TaskProvider<WrapperPatcherTask> taskProvider,
            TaskProvider<Wrapper> wrapperTask,
            WrapperPatchConfig config) {
        taskProvider.configure(task -> {
            task.getPatchHeader().set(config.patchHeader());
            task.getPatchFooter().set(config.patchFooter());
            task.getPatchResource().set(config.patchResource());
            task.getPatchTaskName().set(config.patchTaskName());
            task.getOriginalGradlewScript()
                    .fileProvider(rootProject.provider(() -> wrapperTask.get().getScriptFile()));
            task.getBuildDir().set(task.getTemporaryDir());
            task.getPatchedGradlewScript()
                    .set(rootProject.file(rootProject.getRootDir().toPath().resolve("gradlew")));
        });
    }

    private WrapperPatchRegistrar() {}
}
