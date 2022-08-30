/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import javax.annotation.Nonnull;

import consulo.java.analysis.impl.JavaQuickFixBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.VisibilityUtil;

public class AddDefaultConstructorFix extends AddMethodFix {
  public AddDefaultConstructorFix(PsiClass aClass) {
    this(aClass, PsiUtil.getMaximumModifierForMember(aClass, false));
  }

  public AddDefaultConstructorFix(PsiClass aClass, @Nonnull @PsiModifier.ModifierConstant final String modifier) {
    super(generateConstructor(aClass.getName(), modifier), aClass);
    setText(JavaQuickFixBundle.message("add.default.constructor.text", VisibilityUtil.toPresentableText(modifier), aClass.getName()));
  }

  private static String generateConstructor(final String className, @PsiModifier.ModifierConstant final String modifier) {
    if (modifier.equals(PsiModifier.PACKAGE_LOCAL)) {
      return className + "() {}";
    }
    return modifier + " " + className + "() {}";
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("add.default.constructor.family");
  }
}
