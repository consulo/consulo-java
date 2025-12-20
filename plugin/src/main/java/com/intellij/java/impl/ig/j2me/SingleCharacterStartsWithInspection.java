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
package com.intellij.java.impl.ig.j2me;

import com.intellij.java.language.psi.*;
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
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SingleCharacterStartsWithInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.singleCharacterStartswithDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.singleCharacterStartswithProblemDescriptor().get();
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new SingleCharacterStartsWithFix();
    }

    private static class SingleCharacterStartsWithFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.singleCharacterStartswithQuickfix();
        }

        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiReferenceExpression methodExpression = (PsiReferenceExpression) element.getParent();
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression) methodExpression.getParent();
            PsiElement qualifier = methodExpression.getQualifier();
            if (qualifier == null) {
                return;
            }
            PsiExpressionList argumentList = methodCall.getArgumentList();
            PsiExpression[] expressions = argumentList.getExpressions();
            PsiExpression expression = expressions[0];
            String expressionText = expression.getText();
            String character = expressionText.substring(1, expressionText.length() - 1);
            if (character.equals("'")) {
                character = "\\'";
            }
            String qualifierText = qualifier.getText();
            @NonNls String newExpression;
            String referenceName = methodExpression.getReferenceName();
            if (HardcodedMethodConstants.STARTS_WITH.equals(referenceName)) {
                newExpression = qualifierText + ".length() > 0 && " +
                    qualifierText + ".charAt(0) == '" + character + '\'';
            }
            else {
                newExpression = qualifierText + ".length() > 0 && " +
                    qualifierText + ".charAt(" + qualifierText +
                    ".length() - 1) == '" + character + '\'';
            }
            replaceExpression(methodCall, newExpression);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SingleCharacterStartsWithVisitor();
    }

    private static class SingleCharacterStartsWithVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            PsiReferenceExpression methodExpression = call.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.STARTS_WITH.equals(methodName) &&
                !HardcodedMethodConstants.ENDS_WITH.equals(methodName)) {
                return;
            }
            PsiExpressionList argumentList = call.getArgumentList();
            PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 1 && args.length != 2) {
                return;
            }
            if (!isSingleCharacterStringLiteral(args[0])) {
                return;
            }
            PsiExpression qualifier =
                methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            PsiType type = qualifier.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            registerMethodCallError(call);
        }

        private static boolean isSingleCharacterStringLiteral(PsiExpression arg) {
            PsiType type = arg.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return false;
            }
            if (!(arg instanceof PsiLiteralExpression)) {
                return false;
            }
            PsiLiteralExpression literal = (PsiLiteralExpression) arg;
            String value = (String) literal.getValue();
            if (value == null) {
                return false;
            }
            return value.length() == 1;
        }
    }
}