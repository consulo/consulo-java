/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.annotation;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

import java.util.Collection;

public class AnnotateOverriddenMethodsPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAnnotation)) {
      return false;
    }
    PsiAnnotation annotation = (PsiAnnotation)element;
    String annotationName = annotation.getQualifiedName();
    if (annotationName == null) {
      return false;
    }
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiModifierList)) {
      return false;
    }
    PsiElement grandParent = parent.getParent();
    PsiMethod method;
    int parameterIndex;
    if (!(grandParent instanceof PsiMethod)) {
      if (!(grandParent instanceof PsiParameter)) {
        return false;
      }
      PsiParameter parameter = (PsiParameter)grandParent;
      PsiElement greatGrandParent = grandParent.getParent();
      if (!(greatGrandParent instanceof PsiParameterList)) {
        return false;
      }
      PsiParameterList parameterList =
        (PsiParameterList)greatGrandParent;
      parameterIndex = parameterList.getParameterIndex(parameter);
      PsiElement greatGreatGrandParent =
        greatGrandParent.getParent();
      if (!(greatGreatGrandParent instanceof PsiMethod)) {
        return false;
      }
      method = (PsiMethod)greatGreatGrandParent;
    }
    else {
      parameterIndex = -1;
      method = (PsiMethod)grandParent;
    }
    Project project = element.getProject();
    Collection<PsiMethod> overridingMethods =
      OverridingMethodsSearch.search(method,
                                     GlobalSearchScope.allScope(project), true).findAll();
    if (overridingMethods.isEmpty()) {
      return false;
    }
    for (PsiMethod overridingMethod : overridingMethods) {
      if (parameterIndex == -1) {
        PsiAnnotation foundAnnotation =
          AnnotationUtil.findAnnotation(overridingMethod,
                                        annotationName);
        if (foundAnnotation == null) {
          return true;
        }
      }
      else {
        PsiParameterList parameterList =
          overridingMethod.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();
        PsiParameter parameter = parameters[parameterIndex];
        PsiAnnotation foundAnnotation =
          AnnotationUtil.findAnnotation(parameter,
                                        annotationName);
        if (foundAnnotation == null) {
          return true;
        }
      }
    }
    return false;
  }
}
