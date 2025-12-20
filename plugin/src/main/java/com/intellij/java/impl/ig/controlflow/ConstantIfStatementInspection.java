/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.psiutils.VariableSearchUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ConstantIfStatementInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.constantIfStatementDisplayName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.constantIfStatementProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ConstantIfStatementVisitor();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        //if (PsiUtil.isInJspFile(location)) {
        //    return null;
        //}
        return new ConstantIfStatementFix();
    }

    private static class ConstantIfStatementFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.constantConditionalExpressionSimplifyQuickfix();
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement ifKeyword = descriptor.getPsiElement();
            PsiIfStatement statement = (PsiIfStatement) ifKeyword.getParent();
            assert statement != null;
            PsiStatement thenBranch = statement.getThenBranch();
            PsiStatement elseBranch = statement.getElseBranch();
            PsiExpression condition = statement.getCondition();
            if (BoolUtils.isFalse(condition)) {
                if (elseBranch != null) {
                    replaceStatementWithUnwrapping(elseBranch, statement);
                }
                else {
                    deleteElement(statement);
                }
            }
            else {
                replaceStatementWithUnwrapping(thenBranch, statement);
            }
        }

        private static void replaceStatementWithUnwrapping(
            PsiStatement branch,
            PsiIfStatement statement
        ) throws IncorrectOperationException {
            if (branch instanceof PsiBlockStatement && !(statement.getParent() instanceof PsiIfStatement)) {
                PsiCodeBlock parentBlock = PsiTreeUtil.getParentOfType(branch, PsiCodeBlock.class);
                if (parentBlock == null) {
                    String elseText = branch.getText();
                    replaceStatement(statement, elseText);
                    return;
                }
                PsiCodeBlock block = ((PsiBlockStatement) branch).getCodeBlock();
                boolean hasConflicts = VariableSearchUtils.containsConflictingDeclarations(block, parentBlock);
                if (hasConflicts) {
                    String elseText = branch.getText();
                    replaceStatement(statement, elseText);
                }
                else {
                    PsiElement containingElement = statement.getParent();
                    PsiStatement[] statements = block.getStatements();
                    if (statements.length > 0) {
                        assert containingElement != null;
                        PsiJavaToken lBrace = block.getLBrace();
                        PsiJavaToken rBrace = block.getRBrace();
                        PsiElement added = null;
                        if (lBrace != null && rBrace != null) {
                            PsiElement firstNonBrace = lBrace.getNextSibling();
                            PsiElement lastNonBrace = rBrace.getPrevSibling();
                            if (firstNonBrace != null && lastNonBrace != null) {
                                added = containingElement.addRangeBefore(firstNonBrace, lastNonBrace, statement);
                            }
                        }
                        if (added == null) {
                            added = containingElement.addRangeBefore(statements[0], statements[statements.length - 1], statement);
                        }
                        Project project = statement.getProject();
                        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
                        codeStyleManager.reformat(added);
                    }
                    statement.delete();
                }
            }
            else {
                String elseText = branch.getText();
                replaceStatement(statement, elseText);
            }
        }
    }

    private static class ConstantIfStatementVisitor extends BaseInspectionVisitor {
        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            if (BoolUtils.isTrue(condition) || BoolUtils.isFalse(condition)) {
                registerStatementError(statement);
            }
        }
    }
}