/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class EmptyCatchBlockInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_includeComments = true;
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreTestCases = true;
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreIgnoreParameter = true;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.emptyCatchBlockDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.emptyCatchBlockProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsLocalize.emptyCatchBlockCommentsOption().get(), "m_includeComments");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message("empty.catch.block.ignore.option"), "m_ignoreTestCases");
        optionsPanel.addCheckbox(InspectionGadgetsLocalize.emptyCatchBlockIgnoreIgnoreOption().get(), "m_ignoreIgnoreParameter");
        return optionsPanel;
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new EmptyCatchBlockFix();
    }

    private static class EmptyCatchBlockFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.renameCatchParameterToIgnored();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiCatchSection)) {
                return;
            }
            final PsiCatchSection catchSection = (PsiCatchSection) parent;
            final PsiParameter parameter = catchSection.getParameter();
            if (parameter == null) {
                return;
            }
            final PsiIdentifier identifier = parameter.getNameIdentifier();
            if (identifier == null) {
                return;
            }
            final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            final PsiIdentifier newIdentifier = factory.createIdentifier("ignored");
            identifier.replace(newIdentifier);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new EmptyCatchBlockVisitor();
    }

    private class EmptyCatchBlockVisitor extends BaseInspectionVisitor {
        @Override
        public void visitTryStatement(@Nonnull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            /*if (JspPsiUtil.isInJspFile(statement.getContainingFile())) {
                return;
            }*/
            if (m_ignoreTestCases && TestUtils.isInTestCode(statement)) {
                return;
            }
            final PsiCatchSection[] catchSections = statement.getCatchSections();
            for (final PsiCatchSection section : catchSections) {
                checkCatchSection(section);
            }
        }

        private void checkCatchSection(PsiCatchSection section) {
            final PsiCodeBlock block = section.getCatchBlock();
            if (block == null || !isCatchBlockEmpty(block)) {
                return;
            }
            final PsiParameter parameter = section.getParameter();
            if (parameter == null) {
                return;
            }
            final PsiIdentifier identifier = parameter.getNameIdentifier();
            if (identifier == null) {
                return;
            }
            @NonNls final String parameterName = parameter.getName();
            if (m_ignoreIgnoreParameter && PsiUtil.isIgnoredName(parameterName)) {
                return;
            }
            final PsiElement catchToken = section.getFirstChild();
            if (catchToken == null) {
                return;
            }
            registerError(catchToken);
        }

        private boolean isCatchBlockEmpty(PsiCodeBlock block) {
            if (m_includeComments) {
                final PsiElement[] children = block.getChildren();
                for (final PsiElement child : children) {
                    if (child instanceof PsiComment || child instanceof PsiStatement) {
                        return false;
                    }
                }
                return true;
            }
            else {
                final PsiStatement[] statements = block.getStatements();
                return statements.length == 0;
            }
        }
    }
}