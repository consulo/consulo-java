/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class AddExceptionFromFieldInitializerToConstructorThrowsFix extends BaseIntentionAction implements SyntheticIntentionAction {
  private final static Logger LOG = Logger.getInstance(AddExceptionFromFieldInitializerToConstructorThrowsFix.class);

  private final PsiElement myWrongElement;

  public AddExceptionFromFieldInitializerToConstructorThrowsFix(PsiElement element) {
    myWrongElement = element;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!myWrongElement.isValid()) {
      return false;
    }
    final NavigatablePsiElement maybeField =
      PsiTreeUtil.getParentOfType(myWrongElement, PsiMethod.class, PsiFunctionalExpression.class, PsiField.class);
    if (!(maybeField instanceof PsiField)) {
      return false;
    }
    final PsiField field = (PsiField)maybeField;
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final PsiClass containingClass = field.getContainingClass();
    if ((containingClass == null ||
      containingClass instanceof PsiAnonymousClass ||
      containingClass.isInterface() ||
      !containingClass.isWritable())) {
      return false;
    }
    final List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(field);
    if (exceptions.isEmpty()) {
      return false;
    }
    final PsiMethod[] existedConstructors = containingClass.getConstructors();
    setText(JavaQuickFixLocalize.addExceptionFromFieldInitializerToConstructorThrowsText(existedConstructors.length));
    return true;
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final NavigatablePsiElement field =
      PsiTreeUtil.getParentOfType(myWrongElement, PsiMethod.class, PsiFunctionalExpression.class, PsiField.class);
    if (field instanceof PsiField) {
      final PsiClass aClass = ((PsiField)field).getContainingClass();
      if (aClass != null) {
        PsiMethod[] constructors = aClass.getConstructors();
        if (constructors.length == 0) {
          final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(aClass);
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              defaultConstructorFix.invoke(project, null, file);
            }
          });
          constructors = aClass.getConstructors();
          LOG.assertTrue(constructors.length != 0);
        }

        Set<PsiClassType> unhandledExceptions = new HashSet<PsiClassType>(ExceptionUtil.getUnhandledExceptions(field));
        for (PsiMethod constructor : constructors) {
          AddExceptionToThrowsFix.addExceptionsToThrowsList(project, constructor, unhandledExceptions);
        }
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
