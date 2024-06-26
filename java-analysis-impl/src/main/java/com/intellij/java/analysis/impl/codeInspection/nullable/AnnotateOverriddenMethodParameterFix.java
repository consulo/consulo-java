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
package com.intellij.java.analysis.impl.codeInspection.nullable;

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.util.ClassUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class AnnotateOverriddenMethodParameterFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.AnnotateMethodFix");
  private final String myAnnotation;
  private final String[] myAnnosToRemove;

  public AnnotateOverriddenMethodParameterFix(final String fqn, String... annosToRemove) {
    myAnnotation = fqn;
    myAnnosToRemove = annosToRemove;
  }

  @Override
  @Nonnull
  public String getName() {
    return InspectionLocalize.annotateOverriddenMethodsParameters(ClassUtil.extractClassName(myAnnotation)).get();
  }

  @Override
  @RequiredReadAction
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();

    PsiParameter parameter = PsiTreeUtil.getParentOfType(psiElement, PsiParameter.class, false);
    if (parameter == null) return;
    PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
    if (method == null) return;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    int index = ArrayUtil.find(parameters, parameter);

    List<PsiParameter> toAnnotate = new ArrayList<>();

    PsiMethod[] methods = OverridingMethodsSearch.search(method, GlobalSearchScope.allScope(project), true).toArray(PsiMethod.EMPTY_ARRAY);
    for (PsiMethod psiMethod : methods) {
      PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
      if (index >= psiParameters.length) continue;
      PsiParameter psiParameter = psiParameters[index];
      if (!AnnotationUtil.isAnnotated(psiParameter, myAnnotation, false, false)
        && psiMethod.getManager().isInProject(psiMethod)) {
        toAnnotate.add(psiParameter);
      }
    }

    FileModificationService.getInstance().preparePsiElementsForWrite(toAnnotate);
    for (PsiParameter psiParam : toAnnotate) {
      try {
        assert psiParam != null : toAnnotate;
        if (AnnotationUtil.isAnnotatingApplicable(psiParam, myAnnotation)) {
          new AddAnnotationFix(myAnnotation, psiParam, myAnnosToRemove).invoke(project, null, psiParam.getContainingFile());
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return getName();
  }
}
