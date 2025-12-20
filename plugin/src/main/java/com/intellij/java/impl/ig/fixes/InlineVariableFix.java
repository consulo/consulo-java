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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.impl.ig.psiutils.HighlightUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.application.util.query.Query;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;

public class InlineVariableFix extends InspectionGadgetsFix {

  @Nonnull
  public LocalizeValue getName() {
    return InspectionGadgetsLocalize.inlineVariableQuickfix();
  }

  @Override
  public void doFix(@Nonnull Project project, ProblemDescriptor descriptor) {
    PsiElement nameElement = descriptor.getPsiElement();
    PsiLocalVariable variable =
      (PsiLocalVariable)nameElement.getParent();
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      return;
    }
    if (initializer instanceof PsiArrayInitializerExpression) {
      PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      PsiType type = initializer.getType();
      String typeText;
      if (type == null) {
        typeText = "";
      }
      else {
        typeText = type.getCanonicalText();
      }
      initializer = factory.createExpressionFromText("new " + typeText +
                                                     initializer.getText(), variable);
    }
    PsiMember member =
      PsiTreeUtil.getParentOfType(variable, PsiMember.class);
    Query<PsiReference> search =
      ReferencesSearch.search(variable, new LocalSearchScope(member));
    Collection<PsiElement> replacedElements = new ArrayList<PsiElement>();
    Collection<PsiReference> references = search.findAll();
    for (PsiReference reference : references) {
      PsiElement replacedElement =
        reference.getElement().replace(initializer);
      replacedElements.add(replacedElement);
    }
    HighlightUtils.highlightElements(replacedElements);
    variable.delete();
  }
}