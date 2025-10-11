/*
 * Copyright 2006-2012 Dave Griffith, Bas Leijdekkers
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

package com.intellij.java.impl.ig.security;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class DesignForExtensionInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.designForExtensionDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.designForExtensionProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new DesignForExtensionVisitor();
  }

  private static class DesignForExtensionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
     /* if (JspPsiUtil.isInJspFile(method)) {
        // IDEADEV-25538
        return;
      }   */
      super.visitMethod(method);
      if (method.isConstructor()) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.PRIVATE) ||
          method.hasModifierProperty(PsiModifier.FINAL) ||
          method.hasModifierProperty(PsiModifier.ABSTRACT) ||
          method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isEnum() || containingClass.isInterface() || containingClass.isAnnotationType()) {
        return;
      }
      if (containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (containingClass instanceof PsiAnonymousClass) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return;
      }
      registerMethodError(method);
    }
  }
}