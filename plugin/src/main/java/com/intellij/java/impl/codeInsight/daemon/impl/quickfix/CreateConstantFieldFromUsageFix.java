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

import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.java.analysis.impl.JavaQuickFixBundle;

public class CreateConstantFieldFromUsageFix extends CreateFieldFromUsageFix {
  @Override
  protected boolean createConstantField() {
    return true;
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    String refName = myReferenceExpression.getReferenceName();
    return refName.toUpperCase().equals(refName);
  }

  public CreateConstantFieldFromUsageFix(PsiReferenceExpression referenceElement) {
    super(referenceElement);
    setText(JavaQuickFixBundle.message("create.constant.from.usage.family"));
  }

  @Override
  protected String getText(String varName) {
    return JavaQuickFixBundle.message("create.constant.from.usage.text", varName);
  }
}