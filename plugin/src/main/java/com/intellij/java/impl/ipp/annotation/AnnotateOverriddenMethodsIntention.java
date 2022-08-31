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

import com.intellij.java.language.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.java.language.psi.*;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import javax.annotation.Nonnull;

import java.util.Collection;

public class AnnotateOverriddenMethodsIntention extends MutablyNamedIntention {
  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new AnnotateOverriddenMethodsPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiAnnotation annotation = (PsiAnnotation)element;
    final String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName == null) {
      return null;
    }
    final String annotationName = ClassUtil.extractClassName(qualifiedName);
    final PsiElement grandParent = element.getParent().getParent();
    if (grandParent instanceof PsiMethod) {
      return IntentionPowerPackBundle.message(
        "annotate.overridden.methods.intention.method.name",
        annotationName);
    }
    else {
      return IntentionPowerPackBundle.message(
        "annotate.overridden.methods.intention.parameters.name",
        annotationName);
    }
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element)
    throws IncorrectOperationException {
    final PsiAnnotation annotation = (PsiAnnotation)element;
    final String annotationName = annotation.getQualifiedName();
    if (annotationName == null) {
      return;
    }
    final PsiElement parent = annotation.getParent();
    final PsiElement grandParent = parent.getParent();
    final PsiMethod method;
    final int parameterIndex;
    if (!(grandParent instanceof PsiMethod)) {
      if (!(grandParent instanceof PsiParameter)) {
        return;
      }
      final PsiParameter parameter = (PsiParameter)grandParent;
      final PsiElement greatGrandParent = grandParent.getParent();
      if (!(greatGrandParent instanceof PsiParameterList)) {
        return;
      }
      final PsiParameterList parameterList =
        (PsiParameterList)greatGrandParent;
      parameterIndex = parameterList.getParameterIndex(parameter);
      final PsiElement greatGreatGrandParent =
        greatGrandParent.getParent();
      if (!(greatGreatGrandParent instanceof PsiMethod)) {
        return;
      }
      method = (PsiMethod)greatGreatGrandParent;
    }
    else {
      parameterIndex = -1;
      method = (PsiMethod)grandParent;
    }
    final Project project = element.getProject();
    final Collection<PsiMethod> overridingMethods =
      OverridingMethodsSearch.search(method,
                                     GlobalSearchScope.allScope(project), true).findAll();
    final PsiNameValuePair[] attributes =
      annotation.getParameterList().getAttributes();
    for (PsiMethod overridingMethod : overridingMethods) {
      if (parameterIndex == -1) {
        annotate(overridingMethod, annotationName, attributes, element);
      }
      else {
        final PsiParameterList parameterList =
          overridingMethod.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiParameter parameter = parameters[parameterIndex];
        annotate(parameter, annotationName, attributes, element);
      }
    }
  }

  private static void annotate(PsiModifierListOwner modifierListOwner,
                               String annotationName,
                               PsiNameValuePair[] attributes,
                               PsiElement context) {
    final Project project = context.getProject();
    final ExternalAnnotationsManager annotationsManager =
      ExternalAnnotationsManager.getInstance(project);
    final PsiModifierList modifierList =
      modifierListOwner.getModifierList();
    if (modifierList == null) {
      return;
    }
    if (modifierList.findAnnotation(annotationName) != null) return;
    final ExternalAnnotationsManager.AnnotationPlace
      annotationAnnotationPlace =
      annotationsManager.chooseAnnotationsPlace(modifierListOwner);
    if (annotationAnnotationPlace ==
        ExternalAnnotationsManager.AnnotationPlace.NOWHERE) {
      return;
    }
    final PsiFile fromFile = context.getContainingFile();
    if (annotationAnnotationPlace ==
        ExternalAnnotationsManager.AnnotationPlace.EXTERNAL) {
      annotationsManager.annotateExternally(modifierListOwner,
                                            annotationName, fromFile, attributes);
    }
    else {
      final PsiFile containingFile =
        modifierListOwner.getContainingFile();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(containingFile)) {
        return;
      }
      final PsiAnnotation inserted =
        modifierList.addAnnotation(annotationName);
      for (PsiNameValuePair pair : attributes) {
        inserted.setDeclaredAttributeValue(pair.getName(),
                                           pair.getValue());
      }
      final JavaCodeStyleManager codeStyleManager =
        JavaCodeStyleManager.getInstance(project);
      codeStyleManager.shortenClassReferences(inserted);
      if (containingFile != fromFile) {
        UndoUtil.markPsiFileForUndo(fromFile);
      }
    }
  }
}
