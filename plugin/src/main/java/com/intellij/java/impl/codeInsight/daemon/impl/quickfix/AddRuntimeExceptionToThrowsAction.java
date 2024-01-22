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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import java.util.Collections;

/**
 * @author mike
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.AddRuntimeExceptionToThrowsAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class AddRuntimeExceptionToThrowsAction implements IntentionAction {

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("add.runtime.exception.to.throws.text");
  }

  @Override
  public void invoke(@Nonnull final Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiClassType aClass = getRuntimeExceptionAtCaret(editor, file);
    PsiMethod method = PsiTreeUtil.getParentOfType(elementAtCaret(editor, file), PsiMethod.class);

    AddExceptionToThrowsFix.addExceptionsToThrowsList(project, method, Collections.singleton(aClass));
  }


  private static boolean isMethodThrows(PsiMethod method, PsiClassType exception) {
    PsiClassType[] throwsTypes = method.getThrowsList().getReferencedTypes();
    for (PsiClassType throwsType : throwsTypes) {
      if (throwsType.isAssignableFrom(exception)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    PsiClassType exception = getRuntimeExceptionAtCaret(editor, file);
    if (exception == null) return false;

    PsiMethod method = PsiTreeUtil.getParentOfType(elementAtCaret(editor, file), PsiMethod.class);
    if (method == null || !method.getThrowsList().isPhysical()) return false;

    return !isMethodThrows(method, exception);
  }

  private static PsiClassType getRuntimeExceptionAtCaret(Editor editor, PsiFile file) {
    PsiElement element = elementAtCaret(editor, file);
    if (element == null) return null;
    PsiThrowStatement expression = PsiTreeUtil.getParentOfType(element, PsiThrowStatement.class);
    if (expression == null) return null;
    PsiExpression exception = expression.getException();
    if (exception == null) return null;
    PsiType type = exception.getType();
    if (!(type instanceof PsiClassType)) return null;
    if (!ExceptionUtil.isUncheckedException((PsiClassType)type)) return null;
    return (PsiClassType)type;
  }

  private static PsiElement elementAtCaret(final Editor editor, final PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    return file.findElementAt(offset);
  }
}
