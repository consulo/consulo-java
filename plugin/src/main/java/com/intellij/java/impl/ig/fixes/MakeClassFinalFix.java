/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.application.AccessToken;
import consulo.application.WriteAction;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import consulo.util.collection.MultiMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;

/**
* @author Bas Leijdekkers
*/
public class MakeClassFinalFix extends InspectionGadgetsFix {

  private final String className;

  public MakeClassFinalFix(PsiClass aClass) {
    className = aClass.getName();
  }

  @Override
  @Nonnull
  public String getName() {
    return InspectionGadgetsBundle.message(
      "make.class.final.fix.name", className);
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiElement element = descriptor.getPsiElement();
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (containingClass == null) {
      return;
    }
    final PsiModifierList modifierList = containingClass.getModifierList();
    if (modifierList == null) {
      return;
    }
    if (!isOnTheFly()) {
      if (ClassInheritorsSearch.search(containingClass).findFirst() != null) {
        return;
      }
      modifierList.setModifierProperty(PsiModifier.FINAL, true);
      modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
      return;
    }
    final MultiMap<PsiElement, String> conflicts = new MultiMap();
    final Query<PsiClass> search = ClassInheritorsSearch.search(containingClass);
    search.forEach(new Processor<PsiClass>() {
      @Override
      public boolean process(PsiClass aClass) {
        conflicts.putValue(containingClass, InspectionGadgetsBundle
          .message("0.will.no.longer.be.overridable.by.1", RefactoringUIUtil.getDescription(containingClass, false),
                   RefactoringUIUtil.getDescription(aClass, false)));
        return true;
      }
    });
    final boolean conflictsDialogOK;
    if (!conflicts.isEmpty()) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(element.getProject(), conflicts, new Runnable() {
        @Override
        public void run() {
          final AccessToken token = WriteAction.start();
          try {
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
            modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
          }
          finally {
            token.finish();
          }
        }
      });
      conflictsDialog.show();
      conflictsDialogOK = conflictsDialog.isOK();
    } else {
      conflictsDialogOK = true;
    }
    if (conflictsDialogOK) {
      modifierList.setModifierProperty(PsiModifier.FINAL, true);
      modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
    }
  }
}
