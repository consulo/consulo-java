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
package com.intellij.java.impl.usageView;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.localize.LanguageLocalize;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.ElementDescriptionProvider;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.usage.UsageViewLongNameLocation;
import consulo.usage.UsageViewShortNameLocation;
import consulo.usage.localize.UsageLocalize;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaUsageViewDescriptionProvider implements ElementDescriptionProvider {
    @Override
    @RequiredReadAction
    public LocalizeValue getElementDescription(PsiElement element, ElementDescriptionLocation location) {
        if (location instanceof UsageViewShortNameLocation) {
            if (element instanceof PsiThrowStatement) {
                return UsageLocalize.usageTargetException();
            }
        }

        if (location instanceof UsageViewLongNameLocation) {
            if (element instanceof PsiJavaPackage javaPackage) {
                return LocalizeValue.ofNullable(javaPackage.getQualifiedName());
            }
            else if (element instanceof PsiClass psiClass) {
                if (element instanceof PsiAnonymousClass) {
                    return LanguageLocalize.javaTermsAnonymousClass();
                }
                else {
                    String ret = psiClass.getQualifiedName(); // It happens for local classes
                    if (ret == null) {
                        ret = psiClass.getName();
                    }
                    return LocalizeValue.ofNullable(ret);
                }
            }
            else if (element instanceof PsiVariable variable) {
                return LocalizeValue.ofNullable(variable.getName());
            }
            else if (element instanceof PsiMethod method) {
                return LocalizeValue.of(PsiFormatUtil.formatMethod(
                    method,
                    PsiSubstitutor.EMPTY,
                    PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                    PsiFormatUtil.SHOW_TYPE
                ));
            }
        }

        return LocalizeValue.empty();
    }
}
