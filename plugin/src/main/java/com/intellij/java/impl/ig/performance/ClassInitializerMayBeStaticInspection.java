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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.impl.ig.fixes.ChangeModifierFix;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.component.extension.ExtensionPoint;
import consulo.java.analysis.codeInspection.CantBeStaticCondition;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * @author max
 */
@ExtensionImpl
public class ClassInitializerMayBeStaticInspection extends BaseInspection {
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.classInitializerMayBeStaticProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new ChangeModifierFix(PsiModifier.STATIC);
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.classInitializerMayBeStaticDisplayName();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ClassInitializerCanBeStaticVisitor();
    }

    private static class ClassInitializerCanBeStaticVisitor extends BaseInspectionVisitor {
        @Override
        public void visitClassInitializer(PsiClassInitializer initializer) {
            if (initializer.isStatic()) {
                return;
            }

            PsiClass containingClass = ClassUtils.getContainingClass(initializer);
            if (containingClass == null) {
                return;
            }
            ExtensionPoint<CantBeStaticCondition> cantBeStaticEP =
                initializer.getApplication().getExtensionPoint(CantBeStaticCondition.class);
            if (cantBeStaticEP.anyMatchSafe(addin -> addin.cantBeStatic(initializer))) {
                return;
            }

            PsiElement scope = containingClass.getScope();
            if (!(scope instanceof PsiJavaFile) && !containingClass.isStatic()) {
                return;
            }

            MethodReferenceVisitor visitor = new MethodReferenceVisitor(initializer);
            initializer.accept(visitor);
            if (!visitor.areReferencesStaticallyAccessible()) {
                return;
            }

            registerClassInitializerError(initializer);
        }
    }
}
