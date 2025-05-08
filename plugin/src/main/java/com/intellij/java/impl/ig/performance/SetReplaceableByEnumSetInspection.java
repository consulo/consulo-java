/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class SetReplaceableByEnumSetInspection extends BaseInspection {
    @Override
    @Nonnull
    public String getDisplayName() {
        return InspectionGadgetsLocalize.setReplaceableByEnumSetDisplayName().get();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.setReplaceableByEnumSetProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SetReplaceableByEnumSetVisitor();
    }

    private static class SetReplaceableByEnumSetVisitor extends BaseInspectionVisitor {
        @Override
        public void visitNewExpression(@Nonnull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if (!(type instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType)type;
            if (!classType.hasParameters()) {
                return;
            }
            final PsiType[] typeArguments = classType.getParameters();
            if (typeArguments.length != 1) {
                return;
            }
            final PsiType argumentType = typeArguments[0];
            if (!(argumentType instanceof PsiClassType)) {
                return;
            }
            if (!TypeUtils.expressionHasTypeOrSubtype(expression, JavaClassNames.JAVA_UTIL_SET)) {
                return;
            }
            if (TypeUtils.expressionHasTypeOrSubtype(expression, "java.util.EnumSet")) {
                return;
            }
            final PsiClassType argumentClassType = (PsiClassType)argumentType;
            final PsiClass argumentClass = argumentClassType.resolve();
            if (argumentClass == null) {
                return;
            }
            if (!argumentClass.isEnum()) {
                return;
            }
            registerNewExpressionError(expression);
        }
    }
}
