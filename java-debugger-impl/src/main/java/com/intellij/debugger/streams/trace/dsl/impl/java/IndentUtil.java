// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import jakarta.annotation.Nonnull;

/**
 * Utility class for handling indentation in generated Java code.
 */
final class IndentUtil {
    private static final String INDENT = "  ";

    private IndentUtil() {
    }

    @Nonnull
    static String withIndent(@Nonnull String text, int indent) {
        if (indent <= 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append(INDENT);
        }
        sb.append(text);
        return sb.toString();
    }
}
