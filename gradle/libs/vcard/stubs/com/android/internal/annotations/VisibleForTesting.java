/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Stand-in for the platform-internal annotation the vCard library is annotated with. Inside
 * an Android source tree the real one is provided by the framework.
 */
@Retention(RetentionPolicy.CLASS)
public @interface VisibleForTesting {
    enum Visibility {
        PROTECTED,
        PACKAGE,
        PRIVATE
    }

    Visibility visibility() default Visibility.PRIVATE;
}
