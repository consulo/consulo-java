/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.localize.LanguageLocalize;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

public class HighlightMessageUtil {
    private static final LocalizeValue QUESTION_MARK = LocalizeValue.of("?");

    private HighlightMessageUtil() {
    }

    @Nonnull
    @RequiredReadAction
    public static LocalizeValue getSymbolName(@Nonnull PsiElement symbol, PsiSubstitutor substitutor) {
        return switch (symbol) {
            case PsiAnonymousClass ac -> LanguageLocalize.javaTermsAnonymousClass();
            case PsiClass c -> {
                String n = c.getQualifiedName();
                if (n == null) {
                    n = c.getName();
                }
                yield LocalizeValue.of(n);
            }
            case PsiMethod m -> LocalizeValue.of(PsiFormatUtil.formatMethod(
                m,
                substitutor,
                PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES
            ));
            case PsiVariable v -> LocalizeValue.of(v.getName());
            case PsiJavaPackage jp -> LocalizeValue.of(jp.getQualifiedName());
            case PsiFile f -> {
                PsiDirectory directory = f.getContainingDirectory();
                PsiJavaPackage aPackage = directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
                yield aPackage == null ? QUESTION_MARK : LocalizeValue.of(aPackage.getQualifiedName());
            }
            case PsiDirectory d -> LocalizeValue.of(d.getName());
            default -> QUESTION_MARK;
        };
    }
}
