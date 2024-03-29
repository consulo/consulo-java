/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.numeric;

import jakarta.annotation.Nonnull;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;

@ExtensionImpl
public class LongLiteralsEndingWithLowercaseLInspection
  extends BaseInspection {

  @Nonnull
  public String getID() {
    return "LongLiteralEndingWithLowercaseL";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "long.literals.ending.with.lowercase.l.display.name");
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "long.literals.ending.with.lowercase.l.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new LongLiteralWithLowercaseLVisitor();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new LongLiteralFix();
  }

  private static class LongLiteralFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "long.literals.ending.with.lowercase.l.replace.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiExpression literal =
        (PsiExpression)descriptor.getPsiElement();
      final String text = literal.getText();
      final String newText = text.replace('l', 'L');
      replaceExpression(literal, newText);
    }
  }

  private static class LongLiteralWithLowercaseLVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(
      @Nonnull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (!type.equals(PsiType.LONG)) {
        return;
      }
      final String text = expression.getText();
      if (text == null) {
        return;
      }
      final int length = text.length();
      if (length == 0) {
        return;
      }
      if (text.charAt(length - 1) != 'l') {
        return;
      }
      registerError(expression);
    }
  }
}