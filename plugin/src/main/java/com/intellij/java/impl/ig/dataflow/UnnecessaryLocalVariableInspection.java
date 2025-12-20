/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.dataflow;

import com.intellij.java.impl.ig.fixes.InlineVariableFix;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public abstract class UnnecessaryLocalVariableInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreImmediatelyReturnedVariables = false;

    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreAnnotatedVariables = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.redundantLocalVariableDisplayName();
    }

    @Override
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.redundantLocalVariableIgnoreOption().get(),
            "m_ignoreImmediatelyReturnedVariables"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.redundantLocalVariableAnnotationOption().get(),
            "m_ignoreAnnotatedVariables"
        );
        return optionsPanel;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.unnecessaryLocalVariableProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new InlineVariableFix();
    }

    @Override
    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryLocalVariableVisitor();
    }

    private class UnnecessaryLocalVariableVisitor extends BaseInspectionVisitor {

        @SuppressWarnings({"IfStatementWithIdenticalBranches"})
        @Override
        public void visitLocalVariable(@Nonnull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            if (m_ignoreAnnotatedVariables) {
                PsiModifierList list = variable.getModifierList();
                if (list != null && list.getAnnotations().length > 0) {
                    return;
                }
            }
            if (isCopyVariable(variable)) {
                registerVariableError(variable);
            }
            else if (!m_ignoreImmediatelyReturnedVariables && isImmediatelyReturned(variable)) {
                registerVariableError(variable);
            }
            else if (!m_ignoreImmediatelyReturnedVariables && isImmediatelyThrown(variable)) {
                registerVariableError(variable);
            }
            else if (isImmediatelyAssigned(variable)) {
                registerVariableError(variable);
            }
            else if (isImmediatelyAssignedAsDeclaration(variable)) {
                registerVariableError(variable);
            }
        }

        private boolean isCopyVariable(PsiVariable variable) {
            PsiExpression initializer = variable.getInitializer();
            if (!(initializer instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiReferenceExpression reference = (PsiReferenceExpression) initializer;
            PsiElement referent = reference.resolve();
            if (referent == null) {
                return false;
            }
            if (!(referent instanceof PsiLocalVariable || referent instanceof PsiParameter)) {
                return false;
            }
            PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (containingScope == null) {
                return false;
            }
            if (!variable.hasModifierProperty(PsiModifier.FINAL) &&
                VariableAccessUtils.variableIsAssigned(variable, containingScope, false)) {
                return false;
            }
            PsiVariable initialization = (PsiVariable) referent;
            if (!initialization.hasModifierProperty(PsiModifier.FINAL) &&
                VariableAccessUtils.variableIsAssigned(initialization, containingScope, false)) {
                return false;
            }
            if (!initialization.hasModifierProperty(PsiModifier.FINAL) && variable.hasModifierProperty(PsiModifier.FINAL)) {
                if (VariableAccessUtils.variableIsUsedInInnerClass(variable, containingScope)) {
                    return false;
                }
            }
            return !TypeConversionUtil.boxingConversionApplicable(variable.getType(), initialization.getType());
        }

        private boolean isImmediatelyReturned(PsiVariable variable) {
            PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
            if (containingScope == null) {
                return false;
            }
            PsiElement parent = variable.getParent();
            if (!(parent instanceof PsiDeclarationStatement)) {
                return false;
            }
            PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) parent;
            PsiStatement nextStatement = null;
            PsiStatement[] statements = containingScope.getStatements();
            for (int i = 0; i < (statements.length - 1); i++) {
                if (statements[i].equals(declarationStatement)) {
                    nextStatement = statements[i + 1];
                    break;
                }
            }
            if (!(nextStatement instanceof PsiReturnStatement)) {
                return false;
            }
            PsiReturnStatement returnStatement = (PsiReturnStatement) nextStatement;
            PsiExpression returnValue = returnStatement.getReturnValue();
            if (!(returnValue instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) returnValue;
            PsiElement referent = referenceExpression.resolve();
            if (referent == null || !referent.equals(variable)) {
                return false;
            }
            if (isVariableUsedInFollowingDeclarations(variable, declarationStatement)) {
                return false;
            }
            return true;
        }

        private boolean isImmediatelyThrown(PsiVariable variable) {
            PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
            if (containingScope == null) {
                return false;
            }
            PsiElement parent = variable.getParent();
            if (!(parent instanceof PsiDeclarationStatement)) {
                return false;
            }
            PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) parent;
            PsiStatement nextStatement = null;
            PsiStatement[] statements = containingScope.getStatements();
            for (int i = 0; i < (statements.length - 1); i++) {
                if (statements[i].equals(declarationStatement)) {
                    nextStatement = statements[i + 1];
                    break;
                }
            }
            if (!(nextStatement instanceof PsiThrowStatement)) {
                return false;
            }
            PsiThrowStatement throwStatement = (PsiThrowStatement) nextStatement;
            PsiExpression returnValue = throwStatement.getException();
            if (!(returnValue instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiElement referent = ((PsiReference) returnValue).resolve();
            if (referent == null || !referent.equals(variable)) {
                return false;
            }
            if (isVariableUsedInFollowingDeclarations(variable, declarationStatement)) {
                return false;
            }
            return true;
        }

        private boolean isImmediatelyAssigned(PsiVariable variable) {
            PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
            if (containingScope == null) {
                return false;
            }
            PsiElement parent = variable.getParent();
            if (!(parent instanceof PsiDeclarationStatement)) {
                return false;
            }
            PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) parent;
            PsiStatement nextStatement = null;
            int followingStatementNumber = 0;
            PsiStatement[] statements = containingScope.getStatements();
            for (int i = 0; i < (statements.length - 1); i++) {
                if (statements[i].equals(declarationStatement)) {
                    nextStatement = statements[i + 1];
                    followingStatementNumber = i + 2;
                    break;
                }
            }
            if (!(nextStatement instanceof PsiExpressionStatement)) {
                return false;
            }
            PsiExpressionStatement expressionStatement = (PsiExpressionStatement) nextStatement;
            PsiExpression expression = expressionStatement.getExpression();
            if (!(expression instanceof PsiAssignmentExpression)) {
                return false;
            }
            PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) expression;
            IElementType tokenType = assignmentExpression.getOperationTokenType();
            if (tokenType != JavaTokenType.EQ) {
                return false;
            }
            PsiExpression rhs = assignmentExpression.getRExpression();
            if (!(rhs instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiReferenceExpression reference = (PsiReferenceExpression) rhs;
            PsiElement referent = reference.resolve();
            if (referent == null || !referent.equals(variable)) {
                return false;
            }
            PsiExpression lhs = assignmentExpression.getLExpression();
            if (lhs instanceof PsiArrayAccessExpression) {
                return false;
            }
            if (isVariableUsedInFollowingDeclarations(variable, declarationStatement)) {
                return false;
            }
            for (int i = followingStatementNumber; i < statements.length; i++) {
                if (VariableAccessUtils.variableIsUsed(variable, statements[i])) {
                    return false;
                }
            }
            return true;
        }

        private boolean isImmediatelyAssignedAsDeclaration(PsiVariable variable) {
            PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
            if (containingScope == null) {
                return false;
            }
            PsiElement parent = variable.getParent();
            if (!(parent instanceof PsiDeclarationStatement)) {
                return false;
            }
            PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) parent;
            PsiStatement nextStatement = null;
            int followingStatementNumber = 0;
            PsiStatement[] statements = containingScope.getStatements();
            for (int i = 0; i < (statements.length - 1); i++) {
                if (statements[i].equals(declarationStatement)) {
                    nextStatement = statements[i + 1];
                    followingStatementNumber = i + 2;
                    break;
                }
            }
            if (nextStatement instanceof PsiDeclarationStatement) {
                boolean referenceFound = false;
                PsiDeclarationStatement nextDeclarationStatement = (PsiDeclarationStatement) nextStatement;
                for (PsiElement declaration : nextDeclarationStatement.getDeclaredElements()) {
                    if (!(declaration instanceof PsiVariable)) {
                        continue;
                    }
                    PsiVariable nextVariable = (PsiVariable) declaration;
                    PsiExpression initializer = nextVariable.getInitializer();
                    if (!referenceFound && initializer instanceof PsiReferenceExpression) {
                        PsiReferenceExpression referenceExpression = (PsiReferenceExpression) initializer;
                        PsiElement referent = referenceExpression.resolve();
                        if (variable.equals(referent)) {
                            referenceFound = true;
                            continue;
                        }
                    }
                    if (VariableAccessUtils.variableIsUsed(variable, initializer)) {
                        return false;
                    }
                }
                if (!referenceFound) {
                    return false;
                }
            }
            else if (nextStatement instanceof PsiTryStatement) {
                PsiTryStatement tryStatement = (PsiTryStatement) nextStatement;
                PsiResourceList resourceList = tryStatement.getResourceList();
                if (resourceList == null) {
                    return false;
                }
                boolean referenceFound = false;
                for (PsiResourceVariable resourceVariable : resourceList.getResourceVariables()) {
                    PsiExpression initializer = resourceVariable.getInitializer();
                    if (!referenceFound && initializer instanceof PsiReferenceExpression) {
                        PsiReferenceExpression referenceExpression = (PsiReferenceExpression) initializer;
                        PsiElement referent = referenceExpression.resolve();
                        if (variable.equals(referent)) {
                            referenceFound = true;
                            continue;
                        }
                    }
                    if (VariableAccessUtils.variableIsUsed(variable, initializer)) {
                        return false;
                    }
                }
                if (!referenceFound) {
                    return false;
                }
                if (VariableAccessUtils.variableIsUsed(variable, tryStatement.getTryBlock()) ||
                    VariableAccessUtils.variableIsUsed(variable, tryStatement.getFinallyBlock())) {
                    return false;
                }
                for (PsiCatchSection section : tryStatement.getCatchSections()) {
                    if (VariableAccessUtils.variableIsUsed(variable, section)) {
                        return false;
                    }
                }
            }
            else {
                return false;
            }
            if (isVariableUsedInFollowingDeclarations(variable, declarationStatement)) {
                return false;
            }
            for (int i = followingStatementNumber; i < statements.length; i++) {
                if (VariableAccessUtils.variableIsUsed(variable, statements[i])) {
                    return false;
                }
            }
            return true;
        }

        private boolean isVariableUsedInFollowingDeclarations(PsiVariable variable, PsiDeclarationStatement declarationStatement) {
            PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            if (declaredElements.length == 1) {
                return false;
            }
            boolean check = false;
            for (PsiElement declaredElement : declaredElements) {
                if (!check && variable.equals(declaredElement)) {
                    check = true;
                }
                else {
                    if (VariableAccessUtils.variableIsUsed(variable, declaredElement)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}