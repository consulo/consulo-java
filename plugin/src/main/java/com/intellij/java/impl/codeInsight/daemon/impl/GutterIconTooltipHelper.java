/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.HtmlBuilder;
import consulo.application.util.HtmlChunk;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * @author max
 */
public class GutterIconTooltipHelper {
    private GutterIconTooltipHelper() {
    }

    @RequiredReadAction
    public static HtmlChunk.Element composeText(
        PsiElement[] elements,
        String startHtml,
        BiFunction<String, String, LocalizeValue> pattern
    ) {
        return composeText(Arrays.asList(elements), startHtml, pattern);
    }

    @RequiredReadAction
    public static HtmlChunk.Element composeText(
        Iterable<? extends PsiElement> elements,
        String startHtml,
        BiFunction<String, String, LocalizeValue> pattern
    ) {
        return composeText(elements, startHtml, pattern, "");
    }

    @RequiredReadAction
    public static HtmlChunk.Element composeText(
        Iterable<? extends PsiElement> elements,
        String startHtml,
        BiFunction<String, String, LocalizeValue> pattern,
        String endHtml
    ) {
        Set<LocalizeValue> names = new LinkedHashSet<>();
        for (PsiElement element : elements) {
            if (element instanceof PsiClass psiClass) {
                String className = ClassPresentationUtil.getNameForClass(psiClass, true);
                names.add(pattern.apply("", className));
            }
            else if (element instanceof PsiMethod method) {
                String methodName = method.getName();
                PsiClass aClass = method.getContainingClass();
                String className = aClass == null ? "" : ClassPresentationUtil.getNameForClass(aClass, true);
                names.add(pattern.apply(methodName, className));
            }
            else if (element instanceof PsiFile file) {
                names.add(pattern.apply("", file.getName()));
            }
        }

        HtmlBuilder result = new HtmlBuilder().appendRaw(startHtml);
        boolean first = true;
        for (LocalizeValue name : names) {
            if (!first) {
                result.append(HtmlChunk.br());
            }
            first = false;
            result.append(name);
        }
        result.appendRaw(endHtml);
        return result.wrapWith(HtmlChunk.body()).wrapWith(HtmlChunk.html());
    }
}
