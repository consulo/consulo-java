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

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class StringBufferMustHaveInitialCapacityInspection extends BaseInspection {
    @Override
    @Nonnull
    public String getID() {
        return "StringBufferWithoutInitialCapacity";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.stringBufferMustHaveInitialCapacityDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.stringBufferMustHaveInitialCapacityProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StringBufferInitialCapacityVisitor();
    }

    private static class StringBufferInitialCapacityVisitor extends BaseInspectionVisitor {
        @Override
        public void visitNewExpression(@Nonnull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            PsiType type = expression.getType();

            if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type)
                && !TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUILDER, type)) {
                return;
            }
            PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 0) {
                return;
            }
            registerError(expression);
        }
    }
}