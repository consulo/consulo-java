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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiVariable;
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
public class CStyleArrayDeclarationInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.cStyleArrayDeclarationDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.cStyleArrayDeclarationProblemDescriptor().get();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new CStyleArrayDeclarationFix();
  }

  private static class CStyleArrayDeclarationFix
    extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.cStyleArrayDeclarationReplaceQuickfix();
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiElement nameElement = descriptor.getPsiElement();
      PsiVariable var = (PsiVariable)nameElement.getParent();
      assert var != null;
      var.normalizeDeclaration();
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new CStyleArrayDeclarationVisitor();
  }

  private static class CStyleArrayDeclarationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@Nonnull PsiVariable var) {
      super.visitVariable(var);
      PsiType declaredType = var.getType();
      if (declaredType.getArrayDimensions() == 0) {
        return;
      }
      PsiTypeElement typeElement = var.getTypeElement();
      if (typeElement == null) {
        return; // Could be true for enum constants.
      }
      PsiType elementType = typeElement.getType();
      if (elementType.equals(declaredType)) {
        return;
      }
      registerVariableError(var);
    }
  }
}