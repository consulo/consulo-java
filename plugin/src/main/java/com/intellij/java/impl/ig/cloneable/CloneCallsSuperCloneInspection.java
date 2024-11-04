/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.cloneable;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CloneCallsSuperCloneInspection extends BaseInspection {
    @Nonnull
    @Override
    public String getID() {
        return "CloneDoesntCallSuperClone";
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return InspectionGadgetsLocalize.cloneDoesntCallSuperCloneDisplayName().get();
    }

    @Nonnull
    @Override
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.cloneDoesntCallSuperCloneProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new NoExplicitCloneCallsVisitor();
    }

    private static class NoExplicitCloneCallsVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            //note: no call to super;
            if (!CloneUtils.isClone(method)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.ABSTRACT) || method.hasModifierProperty(PsiModifier.NATIVE)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null || containingClass.isInterface() || containingClass.isAnnotationType()) {
                return;
            }
            if (CloneUtils.onlyThrowsCloneNotSupportedException(method)) {
                if (method.hasModifierProperty(PsiModifier.FINAL) || containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                    return;
                }
            }
            final CallToSuperCloneVisitor visitor = new CallToSuperCloneVisitor();
            method.accept(visitor);
            if (visitor.isCallToSuperCloneFound()) {
                return;
            }
            registerMethodError(method);
        }
    }
}