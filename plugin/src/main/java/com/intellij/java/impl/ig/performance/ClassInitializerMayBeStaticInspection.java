/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.java.impl.ig.performance;

import com.intellij.java.impl.ig.fixes.ChangeModifierFix;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.analysis.codeInspection.CantBeStaticCondition;
import consulo.java.analysis.codeInspection.JavaExtensionPoints;
import consulo.language.psi.PsiElement;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;

/**
 * @author max
 */
@ExtensionImpl
public class ClassInitializerMayBeStaticInspection extends BaseInspection {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("class.initializer.may.be.static.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ChangeModifierFix(PsiModifier.STATIC);
  }

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("class.initializer.may.be.static.display.name");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassInitializerCanBeStaticVisitor();
  }

  private static class ClassInitializerCanBeStaticVisitor extends BaseInspectionVisitor {
    @Override
    public void visitClassInitializer(PsiClassInitializer initializer) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }

      final PsiClass containingClass = ClassUtils.getContainingClass(initializer);
      if (containingClass == null) {
        return;
      }
      for (CantBeStaticCondition addin : JavaExtensionPoints.CANT_BE_STATIC_EP_NAME.getExtensions()) {
        if (addin.cantBeStatic(initializer)) {
          return;
        }
      }
      final PsiElement scope = containingClass.getScope();
      if (!(scope instanceof PsiJavaFile) && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }

      final MethodReferenceVisitor visitor = new MethodReferenceVisitor(initializer);
      initializer.accept(visitor);
      if (!visitor.areReferencesStaticallyAccessible()) {
        return;
      }

      registerClassInitializerError(initializer);
    }
  }
}
