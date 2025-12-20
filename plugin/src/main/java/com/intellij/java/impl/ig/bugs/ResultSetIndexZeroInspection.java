/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ResultSetIndexZeroInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "UseOfIndexZeroInJDBCResultSet";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.use0indexInJdbcResultsetDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return (Boolean) infos[0]
            ? InspectionGadgetsLocalize.use0indexInJdbcResultsetProblemDescriptor().get()
            : InspectionGadgetsLocalize.use0indexInJdbcPreparedStatementProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ResultSetIndexZeroVisitor();
    }

    private static class ResultSetIndexZeroVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            @NonNls String methodName = methodExpression.getReferenceName();
            if (methodName == null) {
                return;
            }
            boolean resultSet;
            if (methodName.startsWith("get") || methodName.startsWith("update")) {
                resultSet = true;
            }
            else if (methodName.startsWith("set")) {
                resultSet = false;
            }
            else {
                return;
            }
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
                return;
            }
            PsiExpression argument = arguments[0];
            Object val = ExpressionUtils.computeConstantExpression(argument);
            if (!(val instanceof Integer) || ((Integer) val).intValue() != 0) {
                return;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (resultSet) {
                if (TypeUtils.expressionHasTypeOrSubtype(qualifier, "java.sql.ResultSet")) {
                    registerError(argument, Boolean.valueOf(resultSet));
                }
            }
            else {
                if (TypeUtils.expressionHasTypeOrSubtype(qualifier, "java.sql.PreparedStatement")) {
                    registerError(argument, Boolean.valueOf(resultSet));
                }
            }
        }
    }
}