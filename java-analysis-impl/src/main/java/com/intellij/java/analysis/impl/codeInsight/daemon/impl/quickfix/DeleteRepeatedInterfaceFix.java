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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.List;

public class DeleteRepeatedInterfaceFix implements SyntheticIntentionAction {
  private final PsiTypeElement myConjunct;
  private final List<PsiTypeElement> myConjList;

  public DeleteRepeatedInterfaceFix(PsiTypeElement conjunct, List<PsiTypeElement> conjList) {
    myConjunct = conjunct;
    myConjList = conjList;
  }

  @Nonnull
  @Override
  public String getText() {
    return "Delete repeated '" + myConjunct.getText() + "'";
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    for (PsiTypeElement element : myConjList) {
      if (!element.isValid()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
      return;
    }
    final PsiTypeCastExpression castExpression = PsiTreeUtil.getParentOfType(myConjunct,
        PsiTypeCastExpression.class);
    if (castExpression != null) {
      final PsiTypeElement castType = castExpression.getCastType();
      if (castType != null) {
        final PsiType type = castType.getType();
        if (type instanceof PsiIntersectionType) {
          final String typeText = StringUtil.join(ContainerUtil.filter(myConjList,
              element -> element != myConjunct), element -> element.getText(), " & ");
          final PsiTypeCastExpression newCastExpr = (PsiTypeCastExpression) JavaPsiFacade.getElementFactory
              (project).createExpressionFromText("(" + typeText + ")a", castType);
          CodeStyleManager.getInstance(project).reformat(castType.replace(newCastExpr.getCastType()));
        }
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
