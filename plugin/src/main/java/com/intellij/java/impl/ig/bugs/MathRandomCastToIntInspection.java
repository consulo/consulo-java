/*
 * Copyright 2011-2012 Bas Leijdekkers
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class MathRandomCastToIntInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.mathRandomCastToIntDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.mathRandomCastToIntProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiTypeCastExpression expression = (PsiTypeCastExpression) infos[0];
        PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiPolyadicExpression)) {
            return null;
        }
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) parent;
        IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (JavaTokenType.ASTERISK != tokenType) {
            return null;
        }
        return new MathRandomCastToIntegerFix();
    }

    private static class MathRandomCastToIntegerFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.mathRandomCastToIntQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiTypeCastExpression)) {
                return;
            }
            PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression) parent;
            PsiElement grandParent = typeCastExpression.getParent();
            if (!(grandParent instanceof PsiPolyadicExpression)) {
                return;
            }
            PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) grandParent;
            PsiExpression operand = typeCastExpression.getOperand();
            if (operand == null) {
                return;
            }
            @NonNls StringBuilder newExpression = new StringBuilder();
            newExpression.append("(int)(");
            PsiExpression[] operands = polyadicExpression.getOperands();
            for (PsiExpression expression : operands) {
                PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(expression);
                if (token != null) {
                    newExpression.append(token.getText());
                }
                if (typeCastExpression.equals(expression)) {
                    newExpression.append(operand.getText());
                }
                else {
                    newExpression.append(expression.getText());
                }
            }
            newExpression.append(')');
            replaceExpression(polyadicExpression, newExpression.toString());
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MathRandomCastToIntegerVisitor();
    }

    private static class MathRandomCastToIntegerVisitor extends BaseInspectionVisitor {

        @Override
        public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            PsiExpression operand = expression.getOperand();
            if (!(operand instanceof PsiMethodCallExpression)) {
                return;
            }
            PsiTypeElement castType = expression.getCastType();
            if (castType == null) {
                return;
            }
            PsiType type = castType.getType();
            if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) {
                return;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) operand;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            @NonNls String referenceName = methodExpression.getReferenceName();
            if (!"random".equals(referenceName)) {
                return;
            }
            PsiMethod method = methodCallExpression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            String qualifiedName = containingClass.getQualifiedName();
            if (!CommonClassNames.JAVA_LANG_MATH.equals(qualifiedName) && !CommonClassNames.JAVA_LANG_STRICT_MATH.equals(qualifiedName)) {
                return;
            }
            registerError(methodCallExpression, expression);
        }
    }
}
