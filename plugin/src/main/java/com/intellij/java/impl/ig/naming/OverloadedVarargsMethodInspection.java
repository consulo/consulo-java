/*
 * Copyright 2006-2007 Bas Leijdekkers
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
package com.intellij.java.impl.ig.naming;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class OverloadedVarargsMethodInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.overloadedVarargMethodDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        final PsiMethod element = (PsiMethod) infos[0];
        return element.isConstructor()
            ? InspectionGadgetsLocalize.overloadedVarargConstructorProblemDescriptor().get()
            : InspectionGadgetsLocalize.overloadedVarargMethodProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new OverloadedVarargMethodVisitor();
    }

    private static class OverloadedVarargMethodVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            if (!method.isVarArgs()) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String methodName = method.getName();
            final PsiMethod[] sameNameMethods = aClass.findMethodsByName(methodName, false);
            for (PsiMethod sameNameMethod : sameNameMethods) {
                if (!sameNameMethod.equals(method)) {
                    registerMethodError(method, method);
                }
            }
        }
    }
}