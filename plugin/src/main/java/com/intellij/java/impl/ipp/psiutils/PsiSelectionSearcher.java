/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.java.impl.ipp.psiutils;

import com.intellij.java.language.psi.JavaRecursiveElementWalkingVisitor;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.document.util.UnfairTextRange;
import consulo.language.psi.*;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class PsiSelectionSearcher {
  private PsiSelectionSearcher() {
  }

  /**
   * Searches elements in selection
   *
   * @param editor          editor to get text selection
   * @param project         Project
   * @param filter          PsiElement filter, e.g. PsiMethodCallExpression.class
   * @param searchChildrenOfFound if true, visitor will look for matching elements in the children of a found element, otherwise will not look inside found element.
   * @param <T>             type based on PsiElement type
   * @return elements in selection
   */
  @Nonnull
  public static <T extends PsiElement> List<T> searchElementsInSelection(Editor editor,
                                                                         Project project,
                                                                         final Class<T> filter,
                                                                         final boolean searchChildrenOfFound) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      return Collections.emptyList();
    }
    final TextRange selection = new UnfairTextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null || file instanceof PsiCompiledElement) {
      return Collections.emptyList();
    }
    final List<T> results = new ArrayList<T>();

    final PsiElementVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!selection.intersects(element.getTextRange())) {
          return;
        }
        if (filter.isAssignableFrom(element.getClass())) {
          results.add((T)element);
          if (!searchChildrenOfFound) {
            return;
          }
        }
        super.visitElement(element);
      }
    };

    file.accept(visitor);
    return results;
  }
}
