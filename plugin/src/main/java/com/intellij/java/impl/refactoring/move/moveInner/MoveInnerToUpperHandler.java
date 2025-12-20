/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.move.moveInner;

import consulo.annotation.component.ExtensionImpl;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.PsiReference;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.move.MoveHandlerDelegate;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.JavaMoveClassesOrPackagesHandler;

import jakarta.annotation.Nullable;

@ExtensionImpl
public class MoveInnerToUpperHandler extends MoveHandlerDelegate {
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
    if (elements.length != 1) return false;
    PsiElement element = elements [0];
    return isNonStaticInnerClass(element) &&
           (targetContainer == null || targetContainer.equals(MoveInnerImpl.getTargetContainer((PsiClass)elements[0], false)));
                                                                        
  }

  private static boolean isNonStaticInnerClass(PsiElement element) {
    return element instanceof PsiClass && element.getParent() instanceof PsiClass &&
           !((PsiClass) element).hasModifierProperty(PsiModifier.STATIC);
  }

  public void doMove(Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {
    MoveInnerImpl.doMove(project, elements, callback);
  }

  public boolean tryToMove(PsiElement element, Project project, DataContext dataContext, PsiReference reference,
                           Editor editor) {
    if (isNonStaticInnerClass(element) && !JavaMoveClassesOrPackagesHandler.isReferenceInAnonymousClass(reference)) {
      PsiClass aClass = (PsiClass) element;
      FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.move.moveInner");
      PsiClass containingClass = aClass.getContainingClass();
     /* if (containingClass instanceof JspClass) {
        CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.message("move.nonstatic.class.from.jsp.not.supported"),
                                            RefactoringBundle.message("move.title"), null);
        return true;
      }  */
      MoveInnerImpl.doMove(project, new PsiElement[]{aClass}, null);
      return true;
    }
    return false;
  }
}
