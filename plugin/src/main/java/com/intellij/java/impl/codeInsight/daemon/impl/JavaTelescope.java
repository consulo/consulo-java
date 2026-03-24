// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.PsiFile;
import consulo.java.localize.JavaLocalize;
import consulo.language.psi.search.ReferencesSearch;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class to support Code Vision for Java.
 */
final class JavaTelescope {
    static final int TOO_MANY_USAGES = -1;
    private static final int MAX_USAGES = 100;

    record UsagesHint(LocalizeValue hint, int count) {
    }

    static @Nullable UsagesHint usagesHint(PsiMember member, PsiFile psiFile) {
        int totalUsageCount = countUsages(member);
        if (totalUsageCount == TOO_MANY_USAGES) return null;
        if (totalUsageCount == 0) return null;
        return new UsagesHint(JavaLocalize.usagesTelescope(totalUsageCount), totalUsageCount);
    }

    private static int countUsages(PsiMember member) {
        AtomicInteger count = new AtomicInteger();
        boolean ok = ReferencesSearch.search(member).forEach(ref -> {
            if (count.incrementAndGet() >= MAX_USAGES) return false;
            return true;
        });
        if (!ok && count.get() >= MAX_USAGES) return TOO_MANY_USAGES;
        return count.get();
    }

    static int collectInheritingClasses(PsiClass aClass) {
        if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
            return 0;
        }
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
            return 0; // It's useless to have overridden markers for Object.
        }

        AtomicInteger count = new AtomicInteger();
        ClassInheritorsSearch.search(aClass).forEach(c -> {
            count.incrementAndGet();
            return true;
        });
        return count.get();
    }

    static int collectOverridingMethods(PsiMethod method) {
        AtomicInteger count = new AtomicInteger();
        OverridingMethodsSearch.search(method).forEach(m -> {
            count.incrementAndGet();
            return true;
        });
        return count.get();
    }
}
