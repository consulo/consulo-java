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
package com.intellij.java.impl.ig.internationalization;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class DateToStringInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "CallToDateToString";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.callToDateTostringDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.callToDateTostringProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new DateToStringVisitor();
    }

    private static class DateToStringVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression expression
        ) {
            super.visitMethodCallExpression(expression);
            String methodName = MethodCallUtils.getMethodName(expression);
            if (!HardcodedMethodConstants.TO_STRING.equals(methodName)) {
                return;
            }
            PsiType targetType = MethodCallUtils.getTargetType(expression);
            if (!TypeUtils.typeEquals(CommonClassNames.JAVA_UTIL_DATE, targetType)) {
                return;
            }
            PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList.getExpressions().length != 0) {
                return;
            }
            if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}