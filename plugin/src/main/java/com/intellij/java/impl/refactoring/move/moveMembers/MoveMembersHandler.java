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
package com.intellij.java.impl.refactoring.move.moveMembers;

import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.move.MoveHandlerDelegate;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class MoveMembersHandler extends MoveHandlerDelegate {
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
    for(PsiElement element: elements) {
      if (!isFieldOrStaticMethod(element)) return false;
    }
    return super.canMove(elements, targetContainer);
  }

  public boolean isValidTarget(PsiElement psiElement, PsiElement[] sources) {
    return psiElement instanceof PsiClass && !(psiElement instanceof PsiAnonymousClass);
  }

  public void doMove(Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {
    MoveMembersImpl.doMove(project, elements, targetContainer, callback);
  }

  public boolean tryToMove(PsiElement element, Project project, DataContext dataContext, PsiReference reference,
                           Editor editor) {
    if (isFieldOrStaticMethod(element)) {
      MoveMembersImpl.doMove(project, new PsiElement[]{element}, null, null);
      return true;
    }
    return false;
  }

  private static boolean isFieldOrStaticMethod(PsiElement element) {
    if (element instanceof PsiField) return true;
    if (element instanceof PsiMethod) {
    //  if (element instanceof JspHolderMethod) return false;
      return ((PsiMethod) element).hasModifierProperty(PsiModifier.STATIC);
    }
    return false;
  }
}
