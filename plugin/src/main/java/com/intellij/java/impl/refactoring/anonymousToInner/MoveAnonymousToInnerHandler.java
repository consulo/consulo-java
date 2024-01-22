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
package com.intellij.java.impl.refactoring.anonymousToInner;

import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiAnonymousClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.editor.refactoring.move.MoveHandlerDelegate;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class MoveAnonymousToInnerHandler extends MoveHandlerDelegate {
  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiAnonymousClass)) return false;
    }
    return super.canMove(elements, targetContainer);
  }

  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (element instanceof PsiAnonymousClass) {
      new AnonymousToInnerHandler().invoke(project, editor, (PsiAnonymousClass)element);
      return true;
    }
    return false;
  }
}
