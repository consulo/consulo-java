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
import consulo.annotation.access.RequiredReadAction;
import consulo.language.localize.LanguageLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public class ClassPresentationUtil {
    private ClassPresentationUtil() {
    }

    @RequiredReadAction
    public static LocalizeValue getNameForClass(PsiClass aClass, boolean qualified) {
        if (aClass instanceof PsiAnonymousClass) {
            if (aClass instanceof PsiEnumConstantInitializer enumInitializer) {
                PsiEnumConstant enumConstant = enumInitializer.getEnumConstant();
                return LanguageLocalize.enumConstantContext(
                    StringUtil.notNullize(enumConstant.getName()),
                    StringUtil.notNullize(getContextName(enumConstant, qualified))
                );
            }
            return LanguageLocalize.anonymousClassContextDisplay(StringUtil.notNullize(getContextName(aClass, qualified)));
        }
        if (qualified) {
            String qName = aClass.getQualifiedName();
            if (qName != null) {
                return LocalizeValue.of(qName);
            }
        }

        String className = aClass.getName();
        String contextName = getContextName(aClass, qualified);
        return contextName != null
            ? LanguageLocalize.classContextDisplay(StringUtil.notNullize(className), contextName)
            : LocalizeValue.ofNullable(className);
    }

    @RequiredReadAction
    private static LocalizeValue getNameForElement(PsiElement element, boolean qualified) {
        if (element instanceof PsiClass psiClass) {
            return getNameForClass(psiClass, qualified);
        }
        else if (element instanceof PsiMethod method) {
            return LanguageLocalize.methodContextDisplay(
                StringUtil.notNullize(method.getName()),
                StringUtil.notNullize(getContextName(method, qualified))
            );
        }
        else if (element instanceof PsiClassOwner) {
            return LocalizeValue.empty();
        }
        else if (element instanceof PsiFile file) {
            return LocalizeValue.of(file.getName());
        }
        else if (element instanceof PsiField field) {
            return LocalizeValue.ofNullable(field.getName());
        }
        else {
            return LocalizeValue.empty();
        }
    }

    @RequiredReadAction
    public static @Nullable String getContextName(PsiElement element, boolean qualified) {
        PsiElement parent = PsiTreeUtil.getStubOrPsiParentOfType(element, PsiMember.class);
        if (parent == null) {
            parent = element.getContainingFile();
        }
        while (true) {
            if (parent == null) {
                return null;
            }
            LocalizeValue name = getNameForElement(parent, qualified);
            if (name.isNotEmpty()) {
                return name.get();
            }
            if (parent instanceof PsiFile) {
                return null;
            }
            parent = PsiTreeUtil.getStubOrPsiParent(parent);
        }
    }

    @RequiredReadAction
    public static LocalizeValue getFunctionalExpressionPresentation(PsiFunctionalExpression functionalExpression, boolean qualified) {
        return LocalizeValue.localizeTODO("Functional expression in " + getContextName(functionalExpression, qualified));
    }
}
