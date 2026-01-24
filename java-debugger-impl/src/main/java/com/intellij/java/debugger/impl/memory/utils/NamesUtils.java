// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.memory.utils;

import consulo.internal.com.sun.jdi.ArrayReference;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.regex.Pattern;

public final class NamesUtils {
    @Nonnull
    public static String getUniqueName(@Nonnull ObjectReference ref) {
        String shortName = StringUtil.getShortName(ref.referenceType().name());
        String name = shortName.replace("[]", "Array");
        return String.format("%s@%d", name, ref.uniqueID());
    }

    @Nonnull
    static String getArrayUniqueName(@Nonnull ArrayReference ref) {
        String shortName = StringUtil.getShortName(ref.referenceType().name());
        int length = ref.length();

        String name = shortName.replaceFirst(Pattern.quote("[]"), String.format("[%d]", length));
        return String.format("%s@%d", name, ref.uniqueID());
    }
}
