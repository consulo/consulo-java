/*
 * Copyright 2003-2005 Dave Griffith
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
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.RefactoringFactory;
import consulo.language.editor.refactoring.RenameRefactoring;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class RenameParameterFix extends InspectionGadgetsFix {
  private final String m_targetName;


  public RenameParameterFix(String targetName) {
    super();
    m_targetName = targetName;
  }

  @Nonnull
  public String getName() {
    return InspectionGadgetsLocalize.renametoQuickfix(m_targetName).get();
  }

  public void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement nameIdentifier = descriptor.getPsiElement();
    final PsiElement elementToRename = nameIdentifier.getParent();
    final RefactoringFactory factory =
      RefactoringFactory.getInstance(project);
    final RenameRefactoring renameRefactoring =
      factory.createRename(elementToRename, m_targetName);
    renameRefactoring.setSearchInComments(false);
    renameRefactoring.setSearchInNonJavaFiles(false);
    renameRefactoring.run();
  }
}
