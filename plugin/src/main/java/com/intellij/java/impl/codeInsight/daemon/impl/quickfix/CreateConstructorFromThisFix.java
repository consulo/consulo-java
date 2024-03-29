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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiTypeParameter;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class CreateConstructorFromThisFix extends CreateConstructorFromThisOrSuperFix {

  public CreateConstructorFromThisFix(PsiMethodCallExpression methodCall) {
    super(methodCall);

    setText(JavaQuickFixBundle.message("create.constructor.from.this.call.family"));
  }

  @Override
  protected String getSyntheticMethodName() {
    return "this";
  }

  @Override
  @Nonnull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    PsiElement e = element;
    do {
      e = PsiTreeUtil.getParentOfType(e, PsiClass.class);
    } while (e instanceof PsiTypeParameter);
    if (e != null && e.isValid() && e.getManager().isInProject(e)) {
      return Collections.singletonList((PsiClass)e);
    }
    else {
      return Collections.emptyList();
    }
  }
}