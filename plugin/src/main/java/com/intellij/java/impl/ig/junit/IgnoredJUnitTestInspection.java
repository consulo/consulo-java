/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class IgnoredJUnitTestInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.ignoredJunitTestDisplayName();
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected String buildErrorString(Object... infos) {
        PsiNamedElement info = (PsiNamedElement) infos[0];
        return info instanceof PsiClass
            ? InspectionGadgetsLocalize.ignoredJunitTestClassproblemDescriptor(info.getName()).get()
            : InspectionGadgetsLocalize.ignoredJunitTestMethodProblemDescriptor(info.getName()).get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new IgnoredJUnitTestVisitor();
    }

    private static class IgnoredJUnitTestVisitor extends BaseInspectionVisitor {

        @Override
        public void visitAnnotation(PsiAnnotation annotation) {
            super.visitAnnotation(annotation);
            PsiModifierListOwner modifierListOwner =
                PsiTreeUtil.getParentOfType(
                    annotation,
                    PsiModifierListOwner.class
                );
            if (!(modifierListOwner instanceof PsiClass ||
                modifierListOwner instanceof PsiMethod)) {
                return;
            }
            PsiJavaCodeReferenceElement nameReferenceElement =
                annotation.getNameReferenceElement();
            if (nameReferenceElement == null) {
                return;
            }
            PsiElement target = nameReferenceElement.resolve();
            if (!(target instanceof PsiClass)) {
                return;
            }
            PsiClass aClass = (PsiClass) target;
            @NonNls String qualifiedName = aClass.getQualifiedName();
            if (!"org.junit.Ignore".equals(qualifiedName)) {
                return;
            }
            registerError(annotation, modifierListOwner);
        }
    }
}
