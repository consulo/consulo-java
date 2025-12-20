/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class UseOfPropertiesAsHashtableInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.propertiesObjectAsHashtableDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.propertiesObjectAsHashtableProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiMethodCallExpression methodCallExpression =
            (PsiMethodCallExpression) infos[0];
        String methodName =
            methodCallExpression.getMethodExpression().getReferenceName();
        boolean put = HardcodedMethodConstants.PUT.equals(methodName);
        if (!(put || HardcodedMethodConstants.GET.equals(methodName))) {
            return null;
        }
        PsiExpressionList argumentList =
            methodCallExpression.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        for (PsiExpression argument : arguments) {
            PsiType type = argument.getType();
            if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                return null;
            }
        }
        return new UseOfPropertiesAsHashtableFix(put);
    }

    private static class UseOfPropertiesAsHashtableFix extends InspectionGadgetsFix {
        private final boolean put;

        public UseOfPropertiesAsHashtableFix(boolean put) {
            this.put = put;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return put
                ? InspectionGadgetsLocalize.propertiesObjectAsHashtableSetQuickfix()
                : InspectionGadgetsLocalize.propertiesObjectAsHashtableGetQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return;
            }
            PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) grandParent;
            PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
            @NonNls StringBuilder newExpression = new StringBuilder();
            PsiExpression qualifierExpression =
                methodExpression.getQualifierExpression();
            if (qualifierExpression != null) {
                newExpression.append(qualifierExpression.getText());
                newExpression.append('.');
            }
            if (put) {
                newExpression.append("setProperty(");
            }
            else {
                newExpression.append("getProperty(");
            }
            PsiExpressionList argumentList =
                methodCallExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            boolean first = true;
            for (PsiExpression argument : arguments) {
                if (!first) {
                    newExpression.append(',');
                }
                else {
                    first = false;
                }
                newExpression.append(argument.getText());
            }
            newExpression.append(')');
            replaceExpression(methodCallExpression, newExpression.toString());
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UseOfPropertiesAsHashtableVisitor();
    }

    private static class UseOfPropertiesAsHashtableVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression expression
        ) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!(HardcodedMethodConstants.PUT.equals(methodName) ||
                HardcodedMethodConstants.PUTALL.equals(methodName) ||
                HardcodedMethodConstants.GET.equals(methodName))) {
                return;
            }
            PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!InheritanceUtil.isInheritor(
                containingClass,
                "java.util.Hashtable"
            )) {
                return;
            }
            PsiExpression qualifier =
                methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_UTIL_PROPERTIES)) {
                return;
            }
            registerMethodCallError(expression, expression);
        }
    }
}
