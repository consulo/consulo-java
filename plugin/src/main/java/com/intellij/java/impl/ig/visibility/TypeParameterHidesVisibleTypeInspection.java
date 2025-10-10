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
package com.intellij.java.impl.ig.visibility;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class TypeParameterHidesVisibleTypeInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.typeParameterHidesVisibleTypeDisplayName();
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsLocalize.typeParameterHidesVisibleTypeProblemDescriptor(aClass.getQualifiedName()).get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new TypeParameterHidesVisibleTypeVisitor();
  }

  private static class TypeParameterHidesVisibleTypeVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitTypeParameter(PsiTypeParameter parameter) {
      super.visitTypeParameter(parameter);
      final String unqualifiedClassName = parameter.getName();

      final JavaPsiFacade manager = JavaPsiFacade.getInstance(parameter.getProject());
      final PsiFile containingFile = parameter.getContainingFile();
      final PsiResolveHelper resolveHelper = manager.getResolveHelper();
      final PsiClass aClass =
        resolveHelper.resolveReferencedClass(unqualifiedClassName,
                                             containingFile);
      if (aClass == null) {
        return;
      }
      final PsiIdentifier identifier = parameter.getNameIdentifier();
      if (identifier == null) {
        return;
      }
      registerError(identifier, aClass);
    }
  }
}