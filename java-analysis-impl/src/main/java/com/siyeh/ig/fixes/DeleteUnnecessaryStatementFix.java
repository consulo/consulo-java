/*
 * Copyright 2003-2018 Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

public class DeleteUnnecessaryStatementFix extends InspectionGadgetsFix {

  private final String name;

  public DeleteUnnecessaryStatementFix(@NonNls String name) {
    this.name = name;
  }

  @Override
  @Nonnull
  public LocalizeValue getName() {
    return InspectionGadgetsLocalize.smthUnnecessaryRemoveQuickfix(name);
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement keywordElement = descriptor.getPsiElement();
    final PsiStatement statement = PsiTreeUtil.getParentOfType(keywordElement, PsiStatement.class);
    if (statement == null) {
      return;
    }
    deleteUnnecessaryStatement(statement);
  }

  public static void deleteUnnecessaryStatement(PsiStatement statement) {
    CommentTracker ct = new CommentTracker();
    final PsiElement parent = statement.getParent();
    if (parent instanceof PsiIfStatement ||
        parent instanceof PsiWhileStatement ||
        parent instanceof PsiDoWhileStatement ||
        parent instanceof PsiForeachStatement ||
        parent instanceof PsiForStatement) {
      ct.replaceAndRestoreComments(statement, "{}");
    } else {
      ct.deleteAndRestoreComments(statement);
    }
  }
}