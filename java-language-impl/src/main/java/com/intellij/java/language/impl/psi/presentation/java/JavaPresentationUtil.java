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
package com.intellij.java.language.impl.psi.presentation.java;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.CodeInsightColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.component.util.Iconable;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.localize.LanguageLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.navigation.ItemPresentation;
import consulo.ui.ex.ColoredItemPresentation;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

public class JavaPresentationUtil {
    private JavaPresentationUtil() {
    }

    public static ColoredItemPresentation getMethodPresentation(final PsiMethod psiMethod) {
        return new ColoredItemPresentation() {
            @Override
            public LocalizeValue getPresentableText() {
                return LocalizeValue.of(PsiFormatUtil.formatMethod(
                    psiMethod,
                    PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                    PsiFormatUtil.SHOW_TYPE
                ));
            }

            @Override
            public TextAttributesKey getTextAttributesKey() {
                if (psiMethod.isDeprecated()) {
                    return CodeInsightColors.DEPRECATED_ATTRIBUTES;
                }
                return null;
            }

            @Override
            @RequiredReadAction
            public String getLocationString() {
                return getJavaSymbolContainerText(psiMethod);
            }

            @Override
            @RequiredReadAction
            public Image getIcon() {
                return IconDescriptorUpdaters.getIcon(psiMethod, Iconable.ICON_FLAG_VISIBILITY);
            }
        };
    }

    public static ItemPresentation getFieldPresentation(final PsiField psiField) {
        return new ColoredItemPresentation() {
            @Override
            public LocalizeValue getPresentableText() {
                return LocalizeValue.ofNullable(psiField.getName());
            }

            @Override
            public TextAttributesKey getTextAttributesKey() {
                if (psiField.isDeprecated()) {
                    return CodeInsightColors.DEPRECATED_ATTRIBUTES;
                }
                return null;
            }

            @Override
            @RequiredReadAction
            public String getLocationString() {
                return getJavaSymbolContainerText(psiField);
            }

            @Override
            @RequiredReadAction
            public Image getIcon() {
                return IconDescriptorUpdaters.getIcon(psiField, Iconable.ICON_FLAG_VISIBILITY);
            }
        };
    }

    @Nullable
    @RequiredReadAction
    private static String getJavaSymbolContainerText(PsiElement element) {
        String result;
        PsiElement container = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiFile.class);

        if (container instanceof PsiClass psiClass) {
            String qName = psiClass.getQualifiedName();
            if (qName != null) {
                result = qName;
            }
            else {
                result = psiClass.getName();
            }
        }
        else if (container instanceof PsiJavaFile javaFile) {
            result = javaFile.getPackageName();
        }
        else {//TODO: local classes
            result = null;
        }
        if (result != null) {
            return LanguageLocalize.auxContextDisplay(result).get();
        }
        return null;
    }
}
