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
package com.intellij.java.impl.ig.resources;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class SocketResourceInspection extends ResourceInspection {
    @SuppressWarnings({"PublicField"})
    public boolean insideTryAllowed = false;

    @Override
    @Nonnull
    public String getID() {
        return "SocketOpenedButNotSafelyClosed";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.socketOpenedNotClosedDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiExpression expression = (PsiExpression) infos[0];
        PsiType type = expression.getType();
        assert type != null;
        String text = type.getPresentableText();
        return InspectionGadgetsLocalize.resourceOpenedNotClosedProblemDescriptor(text).get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.allowResourceToBeOpenedInsideATryBlock();
        return new SingleCheckboxOptionsPanel(message.get(), this, "insideTryAllowed");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SocketResourceVisitor();
    }

    private class SocketResourceVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isSocketFactoryMethod(expression)) {
                return;
            }
            PsiElement parent = getExpressionParent(expression);
            if (parent instanceof PsiReturnStatement || parent instanceof PsiResourceVariable) {
                return;
            }
            PsiVariable boundVariable = getVariable(parent);
            if (isSafelyClosed(boundVariable, expression, insideTryAllowed)) {
                return;
            }
            if (isResourceEscapedFromMethod(boundVariable, expression)) {
                return;
            }
            registerError(expression, expression);
        }

        @Override
        public void visitNewExpression(@Nonnull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if (!isSocketResource(expression)) {
                return;
            }
            PsiElement parent = getExpressionParent(expression);
            if (parent instanceof PsiReturnStatement || parent instanceof PsiResourceVariable) {
                return;
            }
            PsiVariable boundVariable = getVariable(parent);
            if (isSafelyClosed(boundVariable, expression, insideTryAllowed)) {
                return;
            }
            if (isResourceEscapedFromMethod(boundVariable, expression)) {
                return;
            }
            registerError(expression, expression);
        }

        private boolean isSocketResource(PsiNewExpression expression) {
            return TypeUtils.expressionHasTypeOrSubtype(
                expression,
                "java.net.Socket",
                "java.net.DatagramSocket",
                "java.net.ServerSocket"
            ) != null;
        }

        private boolean isSocketFactoryMethod(PsiMethodCallExpression expression) {
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!"accept".equals(methodName)) {
                return false;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            return TypeUtils.expressionHasTypeOrSubtype(qualifier, "java.net.ServerSocket");
        }
    }
}
