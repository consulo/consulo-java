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
package com.intellij.java.impl.codeInsight.highlighting;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.highlight.HighlightUsagesDescriptionLocation;
import consulo.language.localize.LanguageLocalize;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.ElementDescriptionProvider;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaHighlightUsagesDescriptionProvider implements ElementDescriptionProvider {
    @Override
    @RequiredReadAction
    public LocalizeValue getElementDescription(PsiElement element, ElementDescriptionLocation location) {
        if (!(location instanceof HighlightUsagesDescriptionLocation)) {
            return LocalizeValue.empty();
        }

        if (element instanceof PsiClass psiClass) {
            String elementName = psiClass.getQualifiedName();
            if (elementName == null) {
                elementName = psiClass.getName();
            }
            return LocalizeValue.join(
                psiClass.isInterface() ? LanguageLocalize.javaTermsInterface() : LanguageLocalize.javaTermsClass(),
                LocalizeValue.space(),
                LocalizeValue.ofNullable(elementName)
            );
        }
        else if (element instanceof PsiMethod method) {
            String elementName = PsiFormatUtil.formatMethod(
                method,
                PsiSubstitutor.EMPTY,
                PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                PsiFormatUtilBase.SHOW_TYPE
            );
            return LocalizeValue.join(LanguageLocalize.javaTermsMethod(), LocalizeValue.space(), LocalizeValue.of(elementName));
        }
        else if (element instanceof PsiVariable variable) {
            String elementName = PsiFormatUtil.formatVariable(
                variable,
                PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                PsiSubstitutor.EMPTY
            );
            if (element instanceof PsiField) {
                return LocalizeValue.join(LanguageLocalize.javaTermsField(), LocalizeValue.space(), LocalizeValue.of(elementName));
            }
            else if (element instanceof PsiParameter) {
                return LocalizeValue.join(LanguageLocalize.javaTermsParameter(), LocalizeValue.space(), LocalizeValue.of(elementName));
            }
            else {
                return LocalizeValue.join(LanguageLocalize.javaTermsVariable(), LocalizeValue.space(), LocalizeValue.of(elementName));
            }
        }
        else if (element instanceof PsiJavaPackage javaPackage) {
            return LocalizeValue.join(
                LanguageLocalize.javaTermsPackage(),
                LocalizeValue.space(),
                LocalizeValue.ofNullable(javaPackage.getQualifiedName())
            );
        }
        return LocalizeValue.empty();
    }
}
