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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class SwitchStatementWithConfusingDeclarationInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "LocalVariableUsedAndDeclaredInDifferentSwitchBranches";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.switchStatementWithConfusingDeclarationDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.switchStatementWithConfusingDeclarationProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SwitchStatementWithConfusingDeclarationVisitor();
    }

    private static class SwitchStatementWithConfusingDeclarationVisitor extends BaseInspectionVisitor {

        @Override
        public void visitSwitchStatement(@Nonnull PsiSwitchStatement statement) {
            PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            Set<PsiLocalVariable> variablesInPreviousBranches = new HashSet<PsiLocalVariable>(5);
            Set<PsiLocalVariable> variablesInCurrentBranch = new HashSet<PsiLocalVariable>(5);
            PsiStatement[] statements = body.getStatements();
            LocalVariableAccessVisitor visitor = new LocalVariableAccessVisitor(variablesInPreviousBranches);
            for (PsiStatement child : statements) {
                if (child instanceof PsiDeclarationStatement) {
                    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) child;
                    PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
                    for (PsiElement declaredElement : declaredElements) {
                        if (declaredElement instanceof PsiLocalVariable) {
                            PsiLocalVariable localVariable = (PsiLocalVariable) declaredElement;
                            variablesInCurrentBranch.add(localVariable);
                        }
                    }
                }
                else if (child instanceof PsiBreakStatement) {
                    variablesInPreviousBranches.addAll(variablesInCurrentBranch);
                    variablesInCurrentBranch.clear();
                }
                child.accept(visitor);
            }
        }

        class LocalVariableAccessVisitor extends JavaRecursiveElementVisitor {

            private final Set<PsiLocalVariable> myVariablesInPreviousBranches;

            public LocalVariableAccessVisitor(Set<PsiLocalVariable> variablesInPreviousBranches) {
                myVariablesInPreviousBranches = variablesInPreviousBranches;
            }

            @Override
            public void visitReferenceExpression(@Nonnull PsiReferenceExpression referenceExpression) {
                super.visitReferenceExpression(referenceExpression);
                PsiExpression qualifier = referenceExpression.getQualifierExpression();
                if (qualifier != null) {
                    return;
                }
                PsiElement element = referenceExpression.resolve();
                if (!(element instanceof PsiLocalVariable)) {
                    return;
                }
                PsiLocalVariable accessedVariable = (PsiLocalVariable) element;
                if (myVariablesInPreviousBranches.contains(accessedVariable)) {
                    myVariablesInPreviousBranches.remove(accessedVariable);
                    registerVariableError(accessedVariable);
                }
            }
        }
    }
}