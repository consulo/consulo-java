/*
 * Copyright 2010 Bas Leijdekkers
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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

class RemoveLeadingZeroFix extends InspectionGadgetsFix {

  @Nonnull
  public LocalizeValue getName() {
    return InspectionGadgetsLocalize.removeLeadingZeroToMakeDecimalQuickfix();
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    PsiElement element = descriptor.getPsiElement();
    String text = element.getText();
    int max = text.length() - 1;
    if (max < 1) {
      return;
    }
    int index = 0;
    while (index < max && (text.charAt(index) == '0' || text.charAt(index) == '_')) {
      index++;
    }
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiElementFactory factory = psiFacade.getElementFactory();
    String textWithoutLeadingZeros = text.substring(index);
    PsiExpression decimalNumber =
      factory.createExpressionFromText(textWithoutLeadingZeros,
                                       element);
    element.replace(decimalNumber);
  }
}
