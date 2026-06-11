/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.utils.gradlewpatcher;

import org.immutables.value.Value;

/** Configuration for registering a wrapper patcher. */
@Value.Immutable
public interface WrapperPatchConfig {
    String patchHeader();

    String patchFooter();

    String patchResource();

    String patchTaskName();

    String checkTaskName();
}
