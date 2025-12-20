/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.impl.codeInsight.generation.PsiMethodMember;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.ide.util.MemberChooser;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImplementAbstractClassMethodsFix extends ImplementMethodsFix {
  public ImplementAbstractClassMethodsFix(PsiElement highlightElement) {
    super(highlightElement);
  }

  @Override
  public boolean isAvailable(@Nonnull Project project,
                             @Nonnull PsiFile file,
                             @Nonnull PsiElement startElement,
                             @Nonnull PsiElement endElement) {
    if (startElement instanceof PsiNewExpression) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      String startElementText = startElement.getText();
      try {
        PsiNewExpression newExpression =
            (PsiNewExpression) elementFactory.createExpressionFromText(startElementText + "{}", startElement);
        if (newExpression.getAnonymousClass() == null) {
          try {
            newExpression = (PsiNewExpression) elementFactory.createExpressionFromText(startElementText + "){}", startElement);
          } catch (IncorrectOperationException e) {
            return false;
          }
          if (newExpression.getAnonymousClass() == null) return false;
        }
      } catch (IncorrectOperationException e) {
        return false;
      }
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@Nonnull final Project project,
                     @Nonnull PsiFile file,
                     @Nullable final Editor editor,
                     @Nonnull final PsiElement startElement,
                     @Nonnull PsiElement endElement) {
    PsiFile containingFile = startElement.getContainingFile();
    if (editor == null || !FileModificationService.getInstance().prepareFileForWrite(containingFile)) return;
    PsiJavaCodeReferenceElement classReference = ((PsiNewExpression) startElement).getClassReference();
    if (classReference == null) return;
    PsiClass psiClass = (PsiClass) classReference.resolve();
    if (psiClass == null) return;
    final MemberChooser<PsiMethodMember> chooser = chooseMethodsToImplement(editor, startElement, psiClass, false);
    if (chooser == null) return;

    final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.isEmpty()) return;

    new WriteCommandAction(project, file) {
      @Override
      protected void run(Result result) throws Throwable {
        PsiNewExpression newExpression =
            (PsiNewExpression) JavaPsiFacade.getElementFactory(project).createExpressionFromText(startElement.getText() + "{}", startElement);
        newExpression = (PsiNewExpression) startElement.replace(newExpression);
        PsiClass psiClass = newExpression.getAnonymousClass();
        if (psiClass == null) return;
        Map<PsiClass, PsiSubstitutor> subst = new HashMap<PsiClass, PsiSubstitutor>();
        for (PsiMethodMember selectedElement : selectedElements) {
          PsiClass baseClass = selectedElement.getElement().getContainingClass();
          if (baseClass != null) {
            PsiSubstitutor substitutor = subst.get(baseClass);
            if (substitutor == null) {
              substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, psiClass, PsiSubstitutor.EMPTY);
              subst.put(baseClass, substitutor);
            }
            selectedElement.setSubstitutor(substitutor);
          }
        }
        OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor, psiClass, selectedElements, chooser.isCopyJavadoc(),
            chooser.isInsertOverrideAnnotation());
      }
    }.execute();
  }
}
