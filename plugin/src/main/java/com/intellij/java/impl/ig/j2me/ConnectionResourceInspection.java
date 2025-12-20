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
package com.intellij.java.impl.ig.j2me;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ConnectionResourceInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "ConnectionOpenedButNotSafelyClosed";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.connectionOpenedNotSafelyClosedDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiType type = (PsiType) infos[0];
        String text = type.getPresentableText();
        return InspectionGadgetsLocalize.resourceOpenedNotClosedProblemDescriptor(text).get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RecordStoreResourceVisitor();
    }

    private static class RecordStoreResourceVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression expression
        ) {
            super.visitMethodCallExpression(expression);
            if (!isConnectionFactoryMethod(expression)) {
                return;
            }
            PsiElement parent = expression.getParent();
            PsiAssignmentExpression assignment;
            if (!(parent instanceof PsiAssignmentExpression)) {
                if (!(parent instanceof PsiTypeCastExpression)) {
                    PsiType type = expression.getType();
                    if (type != null) {
                        registerError(expression, type);
                    }
                    return;
                }
                PsiElement grandParent = parent.getParent();
                if (!(grandParent instanceof PsiAssignmentExpression)) {
                    PsiType type = expression.getType();
                    if (type != null) {
                        registerError(expression, type);
                    }
                    return;
                }
                assignment = (PsiAssignmentExpression) grandParent;
            }
            else {
                assignment = (PsiAssignmentExpression) parent;
            }
            PsiExpression lhs = assignment.getLExpression();
            if (!(lhs instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) lhs;
            PsiElement referent = referenceExpression.resolve();
            if (!(referent instanceof PsiVariable)) {
                return;
            }
            PsiVariable boundVariable = (PsiVariable) referent;
            PsiElement currentContext = expression;
            while (true) {
                PsiTryStatement tryStatement =
                    PsiTreeUtil.getParentOfType(
                        currentContext,
                        PsiTryStatement.class
                    );
                if (tryStatement == null) {
                    PsiType type = expression.getType();
                    if (type != null) {
                        registerError(expression, type);
                    }
                    return;
                }
                if (resourceIsOpenedInTryAndClosedInFinally(
                    tryStatement, expression, boundVariable)) {
                    return;
                }
                currentContext = tryStatement;
            }
        }


        private static boolean resourceIsOpenedInTryAndClosedInFinally(
            PsiTryStatement tryStatement, PsiExpression lhs,
            PsiVariable boundVariable
        ) {
            PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock == null) {
                return false;
            }
            PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return false;
            }
            if (!PsiTreeUtil.isAncestor(tryBlock, lhs, true)) {
                return false;
            }
            return containsResourceClose(finallyBlock, boundVariable);
        }

        private static boolean containsResourceClose(
            PsiCodeBlock finallyBlock, PsiVariable boundVariable
        ) {
            CloseVisitor visitor =
                new CloseVisitor(boundVariable);
            finallyBlock.accept(visitor);
            return visitor.containsStreamClose();
        }

        private static boolean isConnectionFactoryMethod(
            @Nonnull PsiMethodCallExpression expression
        ) {
            PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.OPEN.equals(methodName)) {
                return false;
            }
            PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return false;
            }
            String className = containingClass.getQualifiedName();
            @NonNls String microeditionConnector =
                "javax.microedition.io.Connector";
            return microeditionConnector.equals(className);
        }
    }

    private static class CloseVisitor extends JavaRecursiveElementVisitor {

        private boolean containsClose = false;
        private final PsiVariable objectToClose;

        private CloseVisitor(PsiVariable objectToClose) {
            super();
            this.objectToClose = objectToClose;
        }

        @Override
        public void visitElement(@Nonnull PsiElement element) {
            if (containsClose) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression call
        ) {
            if (containsClose) {
                return;
            }
            super.visitMethodCallExpression(call);
            PsiReferenceExpression methodExpression =
                call.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
                return;
            }
            PsiExpression qualifier =
                methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) qualifier;
            PsiElement referent = referenceExpression.resolve();
            if (referent == null) {
                return;
            }
            if (referent.equals(objectToClose)) {
                containsClose = true;
            }
        }

        public boolean containsStreamClose() {
            return containsClose;
        }
    }
}
