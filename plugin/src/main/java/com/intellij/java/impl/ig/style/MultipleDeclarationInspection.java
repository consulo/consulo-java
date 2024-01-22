/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.NormalizeDeclarationFix;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class MultipleDeclarationInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreForLoopDeclarations = true;

  @Override
  @jakarta.annotation.Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "multiple.declaration.display.name");
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getID() {
    return "MultipleVariablesInDeclaration";
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "multiple.declaration.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "multiple.declaration.option"),
                                          this, "ignoreForLoopDeclarations");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new NormalizeDeclarationFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MultipleDeclarationVisitor();
  }

  private class MultipleDeclarationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitDeclarationStatement(
      PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      if (statement.getDeclaredElements().length <= 1) {
        return;
      }
      final PsiElement parent = statement.getParent();
      if (ignoreForLoopDeclarations &&
          parent instanceof PsiForStatement) {
        return;
      }
      final PsiElement[] declaredElements =
        statement.getDeclaredElements();
      for (int i = 1; i < declaredElements.length; i++) {
        //skip the first one;
        final PsiElement declaredElement = declaredElements[i];
        if (!(declaredElement instanceof PsiVariable)) {
          continue;
        }
        final PsiVariable variable =
          (PsiVariable)declaredElement;
        registerVariableError(variable);
      }
    }

    @Override
    public void visitField(@jakarta.annotation.Nonnull PsiField field) {
      super.visitField(field);
      if (childrenContainTypeElement(field)) {
        return;
      }
      if (field instanceof PsiEnumConstant) {
        return;
      }
      registerFieldError(field);
    }

    public boolean childrenContainTypeElement(PsiElement field) {
      final PsiElement[] children = field.getChildren();
      for (PsiElement aChildren : children) {
        if (aChildren instanceof PsiTypeElement) {
          return true;
        }
      }
      return false;
    }
  }
}