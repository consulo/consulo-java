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
package com.intellij.codeInsight.daemon.impl.quickfix;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.command.undo.UndoUtil;
import consulo.logging.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.java.language.psi.PsiReferenceList;
import com.intellij.util.IncorrectOperationException;
import consulo.java.analysis.impl.JavaQuickFixBundle;

public class MoveBoundClassToFrontFix extends ExtendsListFix {
  private static final Logger LOG = Logger.getInstance(MoveBoundClassToFrontFix.class);
  private final String myName;

  public MoveBoundClassToFrontFix(PsiClass aClass, PsiClassType classToExtendFrom) {
    super(aClass, classToExtendFrom, true);
    myName = JavaQuickFixBundle.message("move.bound.class.to.front.fix.text",
                                    HighlightUtil.formatClass(myClassToExtendFrom),
                                    HighlightUtil.formatClass(aClass));
  }

  @Override
  @Nonnull
  public String getText() {
    return myName;
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("move.class.in.extend.list.family");
  }

  @Override
  public void invoke(@Nonnull Project project,
                     @Nonnull PsiFile file,
                     @Nullable Editor editor,
                     @Nonnull PsiElement startElement,
                     @Nonnull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(myClass.getContainingFile())) return;
    PsiReferenceList extendsList = myClass.getExtendsList();
    if (extendsList == null) return;
    try {
      modifyList(extendsList, false, -1);
      modifyList(extendsList, true, 0);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    UndoUtil.markPsiFileForUndo(file);
  }

  @Override
  public boolean isAvailable(@Nonnull Project project,
                             @Nonnull PsiFile file,
                             @Nonnull PsiElement startElement,
                             @Nonnull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    return
      myClass.isValid()
      && myClass.getManager().isInProject(myClass)
      && myClassToExtendFrom != null
      && myClassToExtendFrom.isValid()
    ;
  }
}
