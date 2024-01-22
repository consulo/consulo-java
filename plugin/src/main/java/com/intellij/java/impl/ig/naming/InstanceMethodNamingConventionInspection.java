/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.naming;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class InstanceMethodNamingConventionInspection extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 4;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("instance.method.naming.convention.display.name");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final String methodName = (String)infos[0];
    if (methodName.length() < getMinLength()) {
      return InspectionGadgetsBundle.message("instance.method.name.convention.problem.descriptor.short");
    }
    else if (methodName.length() > getMaxLength()) {
      return InspectionGadgetsBundle.message("instance.method.name.convention.problem.descriptor.long");
    }
    return InspectionGadgetsBundle.message("instance.method.name.convention.problem.descriptor.regex.mismatch", getRegex());
  }

  @Override
  protected String getDefaultRegex() {
    return "[a-z][A-Za-z\\d]*";
  }

  @Override
  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  @Override
  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      super.visitMethod(method);
      if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      final String name = method.getName();
      if (isValid(name)) {
        return;
      }
      if (!isOnTheFly()) {
        if (MethodUtils.hasSuper(method)) {
          return;
        }
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      registerMethodError(method, name);
    }
  }
}