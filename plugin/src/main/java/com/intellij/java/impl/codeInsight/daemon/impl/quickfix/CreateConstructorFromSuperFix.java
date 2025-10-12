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

/*
 * @author ven
 */
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

public class CreateConstructorFromSuperFix extends CreateConstructorFromThisOrSuperFix {

  public CreateConstructorFromSuperFix(PsiMethodCallExpression methodCall) {
    super(methodCall);
    setText(JavaQuickFixLocalize.createConstructorFromSuperCallFamily());
  }

  @Override
  protected String getSyntheticMethodName() {
    return "super";
  }

  @Override
  @Nonnull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    do {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }
    while (element instanceof PsiTypeParameter);
    PsiClass curClass = (PsiClass)element;
    if (curClass == null || curClass instanceof PsiAnonymousClass) return Collections.emptyList();
    PsiClassType[] extendsTypes = curClass.getExtendsListTypes();
    if (extendsTypes.length == 0) return Collections.emptyList();
    PsiClass aClass = extendsTypes[0].resolve();
    if (aClass instanceof PsiTypeParameter) return Collections.emptyList();
    if (aClass != null && aClass.isValid() && aClass.getManager().isInProject(aClass)) {
      return Collections.singletonList(aClass);
    }
    return Collections.emptyList();
  }
}