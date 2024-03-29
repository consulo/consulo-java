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

import consulo.usage.UsageInfo;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiNewExpression;
import consulo.logging.Logger;

/**
 * @author ven
 */
public class InternalUsageInfo extends UsageInfo {
  private static final Logger LOG = Logger.getInstance(InternalUsageInfo.class);
  public InternalUsageInfo(final PsiElement referenceElement) {
    super(referenceElement);
    LOG.assertTrue(referenceElement instanceof PsiReferenceExpression || referenceElement instanceof PsiNewExpression);
  }

  PsiExpression getQualifier () {
    PsiElement element = getElement();
    if (element instanceof PsiReferenceExpression) {
      return ((PsiReferenceExpression)element).getQualifierExpression();
    }
    else if (element instanceof PsiNewExpression) return ((PsiNewExpression)element).getQualifier();

    return null;
  }
}
