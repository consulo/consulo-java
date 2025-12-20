/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.redundancy;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class UnusedLabelInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.unusedLabelDisplayName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnusedLabelVisitor();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.unusedLabelProblemDescriptor().get();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnusedLabelFix();
  }

  private static class UnusedLabelFix extends InspectionGadgetsFix {

    @Override
    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.unusedLabelRemoveQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiElement label = descriptor.getPsiElement();
      PsiLabeledStatement labeledStatement =
        (PsiLabeledStatement)label.getParent();
      assert labeledStatement != null;
      PsiStatement statement = labeledStatement.getStatement();
      if (statement == null) {
        return;
      }
      String statementText = statement.getText();
      replaceStatement(labeledStatement, statementText);
    }
  }

  private static class UnusedLabelVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLabeledStatement(
      PsiLabeledStatement statement) {
      if (containsBreakOrContinueForLabel(statement)) {
        return;
      }
      PsiIdentifier labelIdentifier =
        statement.getLabelIdentifier();
      registerError(labelIdentifier);
    }

    private static boolean containsBreakOrContinueForLabel(
      PsiLabeledStatement statement) {
      LabelFinder labelFinder = new LabelFinder(statement);
      statement.accept(labelFinder);
      return labelFinder.jumpFound();
    }
  }

  private static class LabelFinder extends JavaRecursiveElementVisitor {

    private boolean found = false;
    private String label = null;

    private LabelFinder(PsiLabeledStatement target) {
      PsiIdentifier labelIdentifier = target.getLabelIdentifier();
      label = labelIdentifier.getText();
    }

    @Override
    public void visitElement(@Nonnull PsiElement element) {
      if (found) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitContinueStatement(
      @Nonnull PsiContinueStatement continueStatement) {
      if (found) {
        return;
      }
      super.visitContinueStatement(continueStatement);
      PsiIdentifier labelIdentifier =
        continueStatement.getLabelIdentifier();
      if (labelMatches(labelIdentifier)) {
        found = true;
      }
    }

    @Override
    public void visitBreakStatement(
      @Nonnull PsiBreakStatement breakStatement) {
      if (found) {
        return;
      }
      super.visitBreakStatement(breakStatement);
      PsiIdentifier labelIdentifier =
        breakStatement.getLabelIdentifier();
      if (labelMatches(labelIdentifier)) {
        found = true;
      }
    }

    private boolean labelMatches(PsiIdentifier labelIdentifier) {
      if (labelIdentifier == null) {
        return false;
      }
      String labelText = labelIdentifier.getText();
      return labelText.equals(label);
    }

    public boolean jumpFound() {
      return found;
    }
  }
}