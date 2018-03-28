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
package com.siyeh.ig.fixes;

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nonnull;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.HighlightUtils;

public class InlineVariableFix extends InspectionGadgetsFix {

  @Nonnull
  public String getName() {
    return InspectionGadgetsBundle.message("inline.variable.quickfix");
  }

  @Override
  public void doFix(@Nonnull final Project project, final ProblemDescriptor descriptor) {
    final PsiElement nameElement = descriptor.getPsiElement();
    final PsiLocalVariable variable =
      (PsiLocalVariable)nameElement.getParent();
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      return;
    }
    if (initializer instanceof PsiArrayInitializerExpression) {
      final PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      final PsiType type = initializer.getType();
      final String typeText;
      if (type == null) {
        typeText = "";
      }
      else {
        typeText = type.getCanonicalText();
      }
      initializer = factory.createExpressionFromText("new " + typeText +
                                                     initializer.getText(), variable);
    }
    final PsiMember member =
      PsiTreeUtil.getParentOfType(variable, PsiMember.class);
    final Query<PsiReference> search =
      ReferencesSearch.search(variable, new LocalSearchScope(member));
    final Collection<PsiElement> replacedElements = new ArrayList<PsiElement>();
    final Collection<PsiReference> references = search.findAll();
    for (PsiReference reference : references) {
      final PsiElement replacedElement =
        reference.getElement().replace(initializer);
      replacedElements.add(replacedElement);
    }
    HighlightUtils.highlightElements(replacedElements);
    variable.delete();
  }
}