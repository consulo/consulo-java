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
package com.intellij.java.impl.refactoring.safeDelete.usageInfo;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.editor.refactoring.safeDelete.usageInfo.SafeDeleteCustomUsageInfo;
import consulo.language.editor.refactoring.safeDelete.usageInfo.SafeDeleteUsageInfo;
import consulo.language.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class SafeDeletePrivatizeMethod extends SafeDeleteUsageInfo implements SafeDeleteCustomUsageInfo {
  public SafeDeletePrivatizeMethod(PsiMethod method, PsiMethod overriddenMethod) {
    super(method, overriddenMethod);
  }

  public PsiMethod getMethod() {
    return (PsiMethod) getElement();
  }

  @Override
  public void performRefactoring() throws IncorrectOperationException {
    PsiUtil.setModifierProperty(getMethod(), PsiModifier.PRIVATE, true);
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(getMethod(), true, CommonClassNames.JAVA_LANG_OVERRIDE);
    if (annotation != null) {
      annotation.delete();
    }
  }
}
