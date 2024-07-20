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
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;

public class InlineVariableFix extends InspectionGadgetsFix {

  @Nonnull
  public String getName() {
    return InspectionGadgetsLocalize.inlineVariableQuickfix().get();
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