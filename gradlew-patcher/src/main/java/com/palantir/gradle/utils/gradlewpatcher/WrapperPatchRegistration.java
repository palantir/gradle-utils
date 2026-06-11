/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.utils.gradlewpatcher;

import org.gradle.api.tasks.TaskProvider;

/** Holds the task providers returned by {@link WrapperPatchRegistrar#register}. */
public record WrapperPatchRegistration(
        TaskProvider<WrapperPatcherTask> patchTask, TaskProvider<WrapperPatcherTask> checkTask) {}
