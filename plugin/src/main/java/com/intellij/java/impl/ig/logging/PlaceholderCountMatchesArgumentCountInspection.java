/*
 * Copyright 2013 Bas Leijdekkers
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
package com.intellij.java.impl.ig.logging;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.util.containers.ContainerUtilRt;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.Set;

@ExtensionImpl
public class PlaceholderCountMatchesArgumentCountInspection extends BaseInspection {
    private static final Set<String> loggingMethodNames = ContainerUtilRt.newHashSet("trace", "debug", "info", "warn", "error");

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.placeholderCountMatchesArgumentCountDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        int argumentCount = (Integer) infos[0];
        int placeholderCount = (Integer) infos[1];
        return argumentCount > placeholderCount
            ? InspectionGadgetsLocalize.placeholderCountMatchesArgumentCountMoreProblemDescriptor(argumentCount, placeholderCount).get()
            : InspectionGadgetsLocalize.placeholderCountMatchesArgumentCountFewerProblemDescriptor(argumentCount, placeholderCount).get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PlaceholderCountMatchesArgumentCountVisitor();
    }

    private static class PlaceholderCountMatchesArgumentCountVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String name = methodExpression.getReferenceName();
            if (!loggingMethodNames.contains(name)) {
                return;
            }
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
                return;
            }
            PsiExpression firstArgument = arguments[0];
            int placeholderCount;
            int argumentCount;
            if (InheritanceUtil.isInheritor(firstArgument.getType(), "org.slf4j.Marker")) {
                if (arguments.length < 2) {
                    return;
                }
                PsiExpression secondArgument = arguments[1];
                if (!ExpressionUtils.hasStringType(secondArgument)) {
                    return;
                }
                String value = (String) ExpressionUtils.computeConstantExpression(secondArgument);
                if (value == null) {
                    return;
                }
                placeholderCount = countPlaceholders(value);
                argumentCount = hasThrowableType(arguments[arguments.length - 1]) ? arguments.length - 3 : arguments.length - 2;
            }
            else if (ExpressionUtils.hasStringType(firstArgument)) {
                String value = (String) ExpressionUtils.computeConstantExpression(firstArgument);
                if (value == null) {
                    return;
                }
                placeholderCount = countPlaceholders(value);
                argumentCount = hasThrowableType(arguments[arguments.length - 1]) ? arguments.length - 2 : arguments.length - 1;
            }
            else {
                return;
            }
            if (placeholderCount == argumentCount) {
                return;
            }
            registerMethodCallError(expression, argumentCount, placeholderCount);
        }

        private static boolean hasThrowableType(PsiExpression lastArgument) {
            return InheritanceUtil.isInheritor(lastArgument.getType(), CommonClassNames.JAVA_LANG_THROWABLE);
        }

        public static int countPlaceholders(String value) {
            int count = 0;
            int index = value.indexOf("{}");
            while (index >= 0) {
                if (index <= 0 || value.charAt(index - 1) != '\\') {
                    count++;
                }
                index = value.indexOf("{}", index + 1);
            }
            return count;
        }
    }
}
