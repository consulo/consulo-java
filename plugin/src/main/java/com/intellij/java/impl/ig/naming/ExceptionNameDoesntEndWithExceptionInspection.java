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
package com.intellij.java.impl.ig.naming;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ExceptionNameDoesntEndWithExceptionInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "ExceptionClassNameDoesntEndWithException";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.exceptionNameDoesntEndWithExceptionDisplayName();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.exceptionNameDoesntEndWithExceptionProblemDescriptor().get();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExceptionNameDoesntEndWithExceptionVisitor();
  }

  private static class ExceptionNameDoesntEndWithExceptionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      // no call to super, so it doesn't drill down into inner classes
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      final String className = aClass.getName();
      if (className == null) {
        return;
      }
      @NonNls final String exception = "Exception";
      if (className.endsWith(exception)) {
        return;
      }
      if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_EXCEPTION)) {
        return;
      }
      registerClassError(aClass);
    }
  }
}