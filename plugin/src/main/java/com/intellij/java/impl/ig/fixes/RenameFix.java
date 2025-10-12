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
package com.intellij.java.impl.ig.fixes;

import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.RefactoringFactory;
import consulo.language.editor.refactoring.RenameRefactoring;
import consulo.language.editor.refactoring.rename.RenameHandler;
import consulo.language.editor.refactoring.rename.RenameHandlerRegistry;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.concurrent.AsyncResult;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

public class RenameFix extends InspectionGadgetsFix {
    private final String m_targetName;
    private boolean m_searchInStrings = true;
    private boolean m_searchInNonJavaFiles = true;

    public RenameFix() {
        m_targetName = null;
    }

    public RenameFix(@NonNls String targetName) {
        m_targetName = targetName;
    }


    public RenameFix(@NonNls String targetName, boolean searchInStrings, boolean searchInNonJavaFiles) {
        m_targetName = targetName;
        m_searchInStrings = searchInStrings;
        m_searchInNonJavaFiles = searchInNonJavaFiles;
    }

    @Override
    @Nonnull
    public LocalizeValue getName() {
        return m_targetName == null
            ? InspectionGadgetsLocalize.renameQuickfix()
            : InspectionGadgetsLocalize.renametoQuickfix(m_targetName);
    }

    public String getTargetName() {
        return m_targetName;
    }

    @Override
    public void doFix(final Project project, final ProblemDescriptor descriptor) {
        final PsiElement nameIdentifier = descriptor.getPsiElement();
        final PsiElement elementToRename = nameIdentifier.getParent();
        if (m_targetName == null) {
            final AsyncResult<DataContext> contextFromFocus = DataManager.getInstance().getDataContextFromFocus();
            contextFromFocus.doWhenDone(context -> {
                final RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
                if (renameHandler == null) {
                    return;
                }
                renameHandler.invoke(project, new PsiElement[]{elementToRename}, context);
            });
        }
        else {
            final RefactoringFactory factory = RefactoringFactory.getInstance(project);
            final RenameRefactoring renameRefactoring = factory.createRename(elementToRename, m_targetName, m_searchInStrings, m_searchInNonJavaFiles);
            renameRefactoring.run();
        }
    }
}