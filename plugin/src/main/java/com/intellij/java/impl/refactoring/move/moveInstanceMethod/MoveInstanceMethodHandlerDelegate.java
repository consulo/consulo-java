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
package com.intellij.java.impl.refactoring.move.moveInstanceMethod;

import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.move.MoveHandlerDelegate;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.JavaMoveClassesOrPackagesHandler;
import javax.annotation.Nullable;

@ExtensionImpl
public class MoveInstanceMethodHandlerDelegate extends MoveHandlerDelegate {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    if (elements.length != 1) return false;
    PsiElement element = elements [0];
    if (!(element instanceof PsiMethod)) return false;
    //if (element instanceof JspHolderMethod) return false;
    PsiMethod method = (PsiMethod) element;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    return super.canMove(elements, targetContainer);
  }

  public boolean isValidTarget(final PsiElement psiElement, PsiElement[] sources) {
    for (PsiElement source : sources) {
      if (JavaMoveClassesOrPackagesHandler.invalid4Move(source)) return false;
    }
    return psiElement instanceof PsiClass && !(psiElement instanceof PsiAnonymousClass);
  }

  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (!method.hasModifierProperty(PsiModifier.STATIC))  {
        new MoveInstanceMethodHandler().invoke(project, new PsiElement[]{method}, dataContext);
        return true;
      }
    }
    return false;
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    new MoveInstanceMethodHandler().invoke(project, elements, null);
  }
}
