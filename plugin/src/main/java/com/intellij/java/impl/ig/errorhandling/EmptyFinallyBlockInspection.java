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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class EmptyFinallyBlockInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.emptyFinallyBlockDisplayName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.emptyFinallyBlockProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        Integer count = (Integer) infos[0];
        if (count == 0) {
            return new RemoveTryFinallyBlockFix();
        }
        else {
            return new RemoveFinallyBlockFix();
        }
    }

    private static class RemoveTryFinallyBlockFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.removeTryFinallyBlockQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
            if (tryStatement == null) {
                return;
            }
            PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            PsiElement parent = tryStatement.getParent();
            if (parent == null) {
                return;
            }

            PsiResourceList resources = tryStatement.getResourceList();
            if (resources != null) {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                for (PsiResourceVariable resource : resources.getResourceVariables()) {
                    PsiStatement statement = factory.createStatementFromText(resource.getText() + ";", parent);
                    parent.addBefore(statement, tryStatement);
                }
            }

            PsiElement first = tryBlock.getFirstBodyElement();
            PsiElement last = tryBlock.getLastBodyElement();
            if (first != null && last != null) {
                parent.addRangeAfter(first, last, tryStatement);
            }

            tryStatement.delete();
        }
    }

    private static class RemoveFinallyBlockFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.removeFinallyBlockQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
            if (tryStatement == null) {
                return;
            }
            PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock == null) {
                return;
            }
            deleteUntilFinally(finallyBlock);
        }

        private static void deleteUntilFinally(PsiElement element) {
            if (element instanceof PsiJavaToken) {
                PsiJavaToken keyword = (PsiJavaToken) element;
                IElementType tokenType = keyword.getTokenType();
                if (tokenType.equals(JavaTokenType.FINALLY_KEYWORD)) {
                    keyword.delete();
                    return;
                }
            }
            deleteUntilFinally(element.getPrevSibling());
            if (!(element instanceof PsiWhiteSpace)) {
                element.delete();
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new EmptyFinallyBlockVisitor();
    }

    private static class EmptyFinallyBlockVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitTryStatement(
            @Nonnull PsiTryStatement statement
        ) {
            super.visitTryStatement(statement);
            /*if (JspPsiUtil.isInJspFile(statement.getContainingFile())) {
                return;
            }*/
            PsiCodeBlock finallyBlock = statement.getFinallyBlock();
            if (finallyBlock == null) {
                return;
            }
            if (finallyBlock.getStatements().length != 0) {
                return;
            }
            PsiCodeBlock[] catchBlocks = statement.getCatchBlocks();
            PsiElement[] children = statement.getChildren();
            for (PsiElement child : children) {
                String childText = child.getText();
                if (PsiKeyword.FINALLY.equals(childText)) {
                    registerError(child, Integer.valueOf(catchBlocks.length));
                    return;
                }
            }
        }
    }
}