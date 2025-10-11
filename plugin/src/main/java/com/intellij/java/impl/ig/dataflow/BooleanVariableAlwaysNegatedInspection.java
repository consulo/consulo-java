/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.dataflow;

import com.intellij.java.impl.refactoring.invertBoolean.InvertBooleanDialog;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

@ExtensionImpl
public class BooleanVariableAlwaysNegatedInspection extends BaseInspection {

  @Nls
  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.booleanVariableAlwaysInvertedDisplayName();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    return variable instanceof PsiField
        ? InspectionGadgetsLocalize.booleanFieldAlwaysInvertedProblemDescriptor().get()
        : InspectionGadgetsLocalize.booleanVariableAlwaysInvertedProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    return new BooleanVariableIsAlwaysNegatedFix(variable.getName());
  }

  private static class BooleanVariableIsAlwaysNegatedFix
    extends InspectionGadgetsFix {

    private final String name;

    public BooleanVariableIsAlwaysNegatedFix(String name) {
      this.name = name;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.booleanVariableAlwaysInvertedQuickfix(name);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiVariable variable =
        PsiTreeUtil.getParentOfType(element, PsiVariable.class);
      if (variable == null) {
        return;
      }
      final InvertBooleanDialog dialog = new InvertBooleanDialog(variable);
      dialog.show();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BooleanVariableAlwaysNegatedVisitor();
  }

  private static class BooleanVariableAlwaysNegatedVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (!isAlwaysInvertedBoolean(field, field.getContainingClass())) {
        return;
      }
      registerVariableError(field, field);
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiCodeBlock codeBlock =
        PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (!isAlwaysInvertedBoolean(variable, codeBlock)) {
        return;
      }
      registerVariableError(variable, variable);
    }

    private static boolean isAlwaysInvertedBoolean(PsiVariable field,
                                                   PsiElement context) {
      final PsiType type = field.getType();
      if (!PsiType.BOOLEAN.equals(type)) {
        return false;
      }
      final AlwaysNegatedVisitor visitor =
        new AlwaysNegatedVisitor(field);
      context.accept(visitor);
      return visitor.isRead() && visitor.isAlwaysNegated();
    }
  }

  private static class AlwaysNegatedVisitor
    extends JavaRecursiveElementVisitor {

    private final PsiVariable variable;
    private boolean alwaysNegated = true;
    private boolean read = false;

    private AlwaysNegatedVisitor(PsiVariable variable) {
      this.variable = variable;
    }

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (!alwaysNegated) {
        return;
      }
      final String referenceName = expression.getReferenceName();
      if (referenceName == null) {
        return;
      }
      if (!referenceName.equals(variable.getName())) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!variable.equals(target)) {
        return;
      }
      if (!PsiUtil.isAccessedForReading(expression)) {
        return;
      }
      read = true;
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiPrefixExpression)) {
        alwaysNegated = false;
        return;
      }
      final PsiPrefixExpression prefixExpression =
        (PsiPrefixExpression)parent;
      final IElementType tokenType =
        prefixExpression.getOperationTokenType();
      if (!JavaTokenType.EXCL.equals(tokenType)) {
        alwaysNegated = false;
      }
    }

    public boolean isAlwaysNegated() {
      return alwaysNegated;
    }

    public boolean isRead() {
      return read;
    }
  }
}
