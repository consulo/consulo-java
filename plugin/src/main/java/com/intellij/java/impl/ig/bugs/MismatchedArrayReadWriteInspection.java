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
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class MismatchedArrayReadWriteInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "MismatchedReadAndWriteOfArray";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.mismatchedReadWriteArrayDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        boolean written = (Boolean) infos[0];
        return written
            ? InspectionGadgetsLocalize.mismatchedReadWriteArrayProblemDescriptorWriteNotRead().get()
            : InspectionGadgetsLocalize.mismatchedReadWriteArrayProblemDescriptorReadNotWrite().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MismatchedArrayReadWriteVisitor();
    }

    private static class MismatchedArrayReadWriteVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitField(@Nonnull PsiField field) {
            super.visitField(field);
            if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            PsiClass containingClass = PsiUtil.getTopLevelClass(field);
            if (!checkVariable(field, containingClass)) {
                return;
            }
            boolean written =
                arrayContentsAreWritten(field, containingClass);
            boolean read = arrayContentsAreRead(field, containingClass);
            if (written == read) {
                return;
            }
            registerFieldError(field, Boolean.valueOf(written));
        }

        @Override
        public void visitLocalVariable(
            @Nonnull PsiLocalVariable variable
        ) {
            super.visitLocalVariable(variable);
            PsiCodeBlock codeBlock =
                PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (!checkVariable(variable, codeBlock)) {
                return;
            }
            boolean written =
                arrayContentsAreWritten(variable, codeBlock);
            boolean read = arrayContentsAreRead(variable, codeBlock);
            if (written == read) {
                return;
            }
            registerVariableError(variable, Boolean.valueOf(written));
        }

        private static boolean checkVariable(
            PsiVariable variable,
            PsiElement context
        ) {
            if (context == null) {
                return false;
            }
            PsiType type = variable.getType();
            if (type.getArrayDimensions() == 0) {
                return false;
            }
            if (VariableAccessUtils.variableIsAssigned(variable, context)) {
                return false;
            }
            if (VariableAccessUtils.variableIsAssignedFrom(variable, context)) {
                return false;
            }
            if (VariableAccessUtils.variableIsReturned(variable, context)) {
                return false;
            }
            return !VariableAccessUtils.variableIsUsedInArrayInitializer(
                variable, context);
        }

        private static boolean arrayContentsAreWritten(
            PsiVariable variable,
            PsiElement context
        ) {
            if (VariableAccessUtils.arrayContentsAreAssigned(variable, context)) {
                return true;
            }
            PsiExpression initializer = variable.getInitializer();
            if (initializer != null && !isDefaultArrayInitializer(initializer)) {
                return true;
            }
            return variableIsWritten(variable, context);
        }

        private static boolean arrayContentsAreRead(
            PsiVariable variable,
            PsiElement context
        ) {
            if (VariableAccessUtils.arrayContentsAreAccessed(variable, context)) {
                return true;
            }
            return variableIsRead(variable, context);
        }

        private static boolean isDefaultArrayInitializer(
            PsiExpression initializer
        ) {
            if (initializer instanceof PsiNewExpression) {
                PsiNewExpression newExpression =
                    (PsiNewExpression) initializer;
                PsiArrayInitializerExpression arrayInitializer =
                    newExpression.getArrayInitializer();
                return arrayInitializer == null ||
                    isDefaultArrayInitializer(arrayInitializer);
            }
            else if (initializer instanceof PsiArrayInitializerExpression) {
                PsiArrayInitializerExpression arrayInitializerExpression =
                    (PsiArrayInitializerExpression) initializer;
                PsiExpression[] initializers =
                    arrayInitializerExpression.getInitializers();
                return initializers.length == 0;
            }
            return false;
        }

        public static boolean variableIsWritten(@Nonnull PsiVariable variable, @Nonnull PsiElement context) {
            VariableReadWriteVisitor visitor =
                new VariableReadWriteVisitor(variable, true);
            context.accept(visitor);
            return visitor.isPassed();
        }

        public static boolean variableIsRead(@Nonnull PsiVariable variable, @Nonnull PsiElement context) {
            VariableReadWriteVisitor visitor =
                new VariableReadWriteVisitor(variable, false);
            context.accept(visitor);
            return visitor.isPassed();
        }

        static class VariableReadWriteVisitor extends JavaRecursiveElementVisitor {

            @Nonnull
            private final PsiVariable variable;
            private final boolean write;
            private boolean passed = false;

            VariableReadWriteVisitor(@Nonnull PsiVariable variable, boolean write) {
                this.variable = variable;
                this.write = write;
            }

            @Override
            public void visitElement(@Nonnull PsiElement element) {
                if (!passed) {
                    super.visitElement(element);
                }
            }

            @Override
            public void visitBinaryExpression(PsiBinaryExpression expression) {
                super.visitBinaryExpression(expression);
                if (write || passed) {
                    return;
                }
                IElementType tokenType = expression.getOperationTokenType();
                if (!JavaTokenType.EQEQ.equals(tokenType) && !JavaTokenType.NE.equals(tokenType)) {
                    return;
                }
                PsiExpression lhs = expression.getLOperand();
                if (!(lhs instanceof PsiBinaryExpression)) {
                    if (VariableAccessUtils.mayEvaluateToVariable(lhs, variable)) {
                        passed = true;
                    }
                }
                PsiExpression rhs = expression.getROperand();
                if (!(rhs instanceof PsiBinaryExpression)) {
                    if (VariableAccessUtils.mayEvaluateToVariable(rhs, variable)) {
                        passed = true;
                    }
                }
            }

            @Override
            public void visitMethodCallExpression(
                @Nonnull PsiMethodCallExpression call
            ) {
                if (passed) {
                    return;
                }
                super.visitMethodCallExpression(call);
                PsiExpressionList argumentList = call.getArgumentList();
                PsiExpression[] arguments = argumentList.getExpressions();
                for (int i = 0; i < arguments.length; i++) {
                    PsiExpression argument = arguments[i];
                    if (VariableAccessUtils.mayEvaluateToVariable(
                        argument,
                        variable
                    )) {
                        if (write && i == 0 && isCallToSystemArraycopy(call)) {
                            return;
                        }
                        if (!write && i == 2 && isCallToSystemArraycopy(call)) {
                            return;
                        }
                        passed = true;
                    }
                }
            }

            private static boolean isCallToSystemArraycopy(
                PsiMethodCallExpression call
            ) {
                PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
                @NonNls String name =
                    methodExpression.getReferenceName();
                if (!"arraycopy".equals(name)) {
                    return false;
                }
                PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
                if (!(qualifier instanceof PsiReferenceExpression)) {
                    return false;
                }
                PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) qualifier;
                PsiElement element =
                    referenceExpression.resolve();
                if (!(element instanceof PsiClass)) {
                    return false;
                }
                PsiClass aClass = (PsiClass) element;
                String qualifiedName =
                    aClass.getQualifiedName();
                return "java.lang.System".equals(qualifiedName);
            }

            @Override
            public void visitNewExpression(
                @Nonnull PsiNewExpression newExpression
            ) {
                if (passed) {
                    return;
                }
                super.visitNewExpression(newExpression);
                PsiExpressionList argumentList =
                    newExpression.getArgumentList();
                if (argumentList == null) {
                    return;
                }
                PsiExpression[] arguments = argumentList.getExpressions();
                for (PsiExpression argument : arguments) {
                    if (VariableAccessUtils.mayEvaluateToVariable(
                        argument,
                        variable
                    )) {
                        passed = true;
                    }
                }
            }

            public boolean isPassed() {
                return passed;
            }
        }
    }
}