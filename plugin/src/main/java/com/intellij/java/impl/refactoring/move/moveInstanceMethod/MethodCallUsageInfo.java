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
package com.intellij.java.impl.refactoring.move.moveInstanceMethod;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethodReferenceExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.usage.UsageInfo;

/**
 * @author ven
 */
public class MethodCallUsageInfo extends UsageInfo {
  private final PsiElement myMethodCallExpression;
  private final boolean myIsInternal;

  public MethodCallUsageInfo(final PsiReferenceExpression referenceExpression, final boolean internal) {
    super(referenceExpression);
    myIsInternal = internal;
    myMethodCallExpression = referenceExpression instanceof PsiMethodReferenceExpression ? referenceExpression : referenceExpression.getParent();
  }

  public PsiElement getMethodCallExpression() {
    return myMethodCallExpression;
  }

  public boolean isInternal() {
    return myIsInternal;
  }
}
