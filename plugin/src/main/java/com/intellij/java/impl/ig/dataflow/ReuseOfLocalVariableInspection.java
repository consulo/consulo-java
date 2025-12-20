/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.psiutils.HighlightUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.content.scope.SearchScope;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class ReuseOfLocalVariableInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.reuseOfLocalVariableDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.reuseOfLocalVariableProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new ReuseOfLocalVariableFix();
    }

    private static class ReuseOfLocalVariableFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.reuseOfLocalVariableSplitQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) descriptor.getPsiElement();
            PsiLocalVariable variable = (PsiLocalVariable) referenceExpression.resolve();
            PsiAssignmentExpression assignment = (PsiAssignmentExpression) referenceExpression.getParent();
            assert assignment != null;
            PsiExpressionStatement assignmentStatement = (PsiExpressionStatement) assignment.getParent();
            PsiExpression lExpression = assignment.getLExpression();
            String originalVariableName = lExpression.getText();
            assert variable != null;
            PsiType type = variable.getType();
            JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
            PsiCodeBlock variableBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            String newVariableName = codeStyleManager.suggestUniqueVariableName(originalVariableName, variableBlock, false);
            PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(assignmentStatement, PsiCodeBlock.class);
            SearchScope scope;
            if (codeBlock != null) {
                scope = new LocalSearchScope(codeBlock);
            }
            else {
                scope = variable.getUseScope();
            }
            Query<PsiReference> query = ReferencesSearch.search(variable, scope, false);
            JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            PsiElementFactory factory = psiFacade.getElementFactory();
            List<PsiReferenceExpression> collectedReferences = new ArrayList<>();
            for (PsiReference reference : query) {
                PsiElement referenceElement = reference.getElement();
                if (referenceElement == null) {
                    continue;
                }
                TextRange textRange = assignmentStatement.getTextRange();
                if (referenceElement.getTextOffset() <= textRange.getEndOffset()) {
                    continue;
                }
                PsiExpression newExpression = factory.createExpressionFromText(newVariableName, referenceElement);
                PsiReferenceExpression replacementExpression = (PsiReferenceExpression) referenceElement.replace(newExpression);
                collectedReferences.add(replacementExpression);
            }
            PsiExpression rhs = assignment.getRExpression();
            String rhsText;
            if (rhs == null) {
                rhsText = "";
            }
            else {
                rhsText = rhs.getText();
            }
            @NonNls String newStatementText = type.getCanonicalText() + ' ' + newVariableName + " =  " + rhsText + ';';

            PsiStatement newStatement = factory.createStatementFromText(newStatementText, assignmentStatement);
            PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) assignmentStatement.replace(newStatement);
            PsiElement[] elements =
                declarationStatement.getDeclaredElements();
            PsiLocalVariable newVariable = (PsiLocalVariable) elements[0];
            PsiElement context = declarationStatement.getParent();
            HighlightUtils.showRenameTemplate(
                context,
                newVariable,
                collectedReferences.toArray(new PsiReferenceExpression[collectedReferences.size()])
            );
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ReuseOfLocalVariableVisitor();
    }

    private static class ReuseOfLocalVariableVisitor extends BaseInspectionVisitor {
        @Override
        public void visitAssignmentExpression(
            @Nonnull PsiAssignmentExpression assignment
        ) {
            super.visitAssignmentExpression(assignment);
            PsiElement assignmentParent = assignment.getParent();
            if (!(assignmentParent instanceof PsiExpressionStatement)) {
                return;
            }
            PsiExpression lhs = assignment.getLExpression();
            if (!(lhs instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
            PsiElement referent = reference.resolve();
            if (!(referent instanceof PsiLocalVariable)) {
                return;
            }
            PsiVariable variable = (PsiVariable) referent;

            //TODO: this is safe, but can be weakened
            if (variable.getInitializer() == null) {
                return;
            }
            IElementType tokenType = assignment.getOperationTokenType();
            if (!JavaTokenType.EQ.equals(tokenType)) {
                return;
            }
            PsiExpression rhs = assignment.getRExpression();
            if (rhs != null && VariableAccessUtils.variableIsUsed(variable, rhs)) {
                return;
            }
            PsiCodeBlock variableBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (variableBlock == null) {
                return;
            }

            if (loopExistsBetween(assignment, variableBlock)) {
                return;
            }
            if (tryExistsBetween(assignment, variableBlock)) {
                // this could be weakened, slightly, if it could be verified
                // that a variable is used in only one branch of a try statement
                return;
            }
            PsiElement assignmentBlock = assignmentParent.getParent();
            if (assignmentBlock == null) {
                return;
            }
            if (variableBlock.equals(assignmentBlock)) {
                registerError(lhs);
            }
            PsiStatement[] statements = variableBlock.getStatements();
            PsiElement containingStatement = getChildWhichContainsElement(variableBlock, assignment);
            int statementPosition = -1;
            for (int i = 0; i < statements.length; i++) {
                if (statements[i].equals(containingStatement)) {
                    statementPosition = i;
                    break;
                }
            }
            if (statementPosition == -1) {
                return;
            }
            for (int i = statementPosition + 1; i < statements.length; i++) {
                if (VariableAccessUtils.variableIsUsed(variable, statements[i])) {
                    return;
                }
            }
            registerError(lhs);
        }

        private static boolean loopExistsBetween(PsiAssignmentExpression assignment, PsiCodeBlock block) {
            PsiElement elementToTest = assignment;
            while (elementToTest != null) {
                if (elementToTest.equals(block)) {
                    return false;
                }
                if (elementToTest instanceof PsiWhileStatement ||
                    elementToTest instanceof PsiForeachStatement ||
                    elementToTest instanceof PsiForStatement ||
                    elementToTest instanceof PsiDoWhileStatement) {
                    return true;
                }
                elementToTest = elementToTest.getParent();
            }
            return false;
        }

        private static boolean tryExistsBetween(
            PsiAssignmentExpression assignment, PsiCodeBlock block
        ) {
            PsiElement elementToTest = assignment;
            while (elementToTest != null) {
                if (elementToTest.equals(block)) {
                    return false;
                }
                if (elementToTest instanceof PsiTryStatement) {
                    return true;
                }
                elementToTest = elementToTest.getParent();
            }
            return false;
        }

        /**
         * @noinspection AssignmentToMethodParameter
         */
        @Nullable
        public static PsiElement getChildWhichContainsElement(
            @Nonnull PsiCodeBlock ancestor,
            @Nonnull PsiElement descendant
        ) {
            PsiElement element = descendant;
            while (!element.equals(ancestor)) {
                descendant = element;
                element = descendant.getParent();
                if (element == null) {
                    return null;
                }
            }
            return descendant;
        }
    }
}