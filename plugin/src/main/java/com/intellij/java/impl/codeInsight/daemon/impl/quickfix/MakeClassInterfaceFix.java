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

import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.ast.IElementType;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MakeClassInterfaceFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(MakeClassInterfaceFix.class);

  private final boolean myMakeInterface;
  private final String myName;

  public MakeClassInterfaceFix(PsiClass aClass, final boolean makeInterface) {
    super(aClass);
    myMakeInterface = makeInterface;
    myName = aClass.getName();
  }

  @Nonnull
  @Override
  public String getText() {
    return JavaQuickFixBundle.message(myMakeInterface? "make.class.an.interface.text":"make.interface.an.class.text", myName);
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("make.class.an.interface.family");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project,
                             @Nonnull PsiFile file,
                             @Nonnull PsiElement startElement,
                             @Nonnull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;

    return myClass.isValid() && myClass.getManager().isInProject(myClass);
  }

  @Override
  public void invoke(@Nonnull Project project,
                     @Nonnull PsiFile file,
                     @Nullable Editor editor,
                     @Nonnull PsiElement startElement,
                     @Nonnull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    if (!FileModificationService.getInstance().preparePsiElementForWrite(myClass)) return;
    try {
      final PsiReferenceList extendsList = myMakeInterface? myClass.getExtendsList() : myClass.getImplementsList();
      final PsiReferenceList implementsList = myMakeInterface? myClass.getImplementsList() : myClass.getExtendsList();
      if (extendsList != null) {
        for (PsiJavaCodeReferenceElement referenceElement : extendsList.getReferenceElements()) {
          referenceElement.delete();
        }
        if (implementsList != null) {
          for (PsiJavaCodeReferenceElement referenceElement : implementsList.getReferenceElements()) {
            extendsList.addAfter(referenceElement, null);
            referenceElement.delete();
          }
        }
      }
      convertPsiClass(myClass, myMakeInterface);
      LanguageUndoUtil.markPsiFileForUndo(file);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void convertPsiClass(PsiClass aClass, final boolean makeInterface) throws IncorrectOperationException {
    final IElementType lookFor = makeInterface? JavaTokenType.CLASS_KEYWORD : JavaTokenType.INTERFACE_KEYWORD;
    final PsiKeyword replaceWith = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createKeyword(makeInterface? PsiKeyword.INTERFACE : PsiKeyword.CLASS);
    for (PsiElement psiElement : aClass.getChildren()) {
      if (psiElement instanceof PsiKeyword) {
        final PsiKeyword psiKeyword = (PsiKeyword)psiElement;
        if (psiKeyword.getTokenType() == lookFor) {
          psiKeyword.replace(replaceWith);
          break;
        }
      }
    }
  }
}
