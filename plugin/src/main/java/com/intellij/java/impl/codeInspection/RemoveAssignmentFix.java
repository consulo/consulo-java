/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredWriteAction;
import consulo.java.analysis.impl.codeInsight.JavaInspectionsBundle;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class RemoveAssignmentFix extends RemoveInitializerFix {
  @Nonnull
  @Override
  public LocalizeValue getName() {
    return JavaInspectionsLocalize.inspectionUnusedAssignmentRemoveAssignmentQuickfix();
  }

  @Override
  @RequiredWriteAction
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent;
    parent = element instanceof PsiReferenceExpression ? element.getParent() : element;
    if (!(parent instanceof PsiAssignmentExpression)) {
      return;
    }
    final PsiExpression rExpression = ((PsiAssignmentExpression) parent).getRExpression();
    final PsiElement gParent = parent.getParent();
    if ((gParent instanceof PsiExpression || gParent instanceof PsiExpressionList) && rExpression != null) {
      if (!FileModificationService.getInstance().prepareFileForWrite(gParent.getContainingFile())) {
        return;
      }
      if (gParent instanceof PsiParenthesizedExpression) {
        gParent.replace(rExpression);
      } else {
        parent.replace(rExpression);
      }
      return;
    }

    PsiElement resolve = null;
    if (element instanceof PsiReferenceExpression referenceExpression) {
      resolve = referenceExpression.resolve();
    } else {
      final PsiExpression lExpr = PsiUtil.deparenthesizeExpression(((PsiAssignmentExpression) parent).getLExpression());
      if (lExpr instanceof PsiReferenceExpression referenceExpression) {
        resolve = referenceExpression.resolve();
      }
    }
    if (!(resolve instanceof PsiVariable)) {
      return;
    }
    sideEffectAwareRemove(project, rExpression, parent, (PsiVariable) resolve);
  }
}
