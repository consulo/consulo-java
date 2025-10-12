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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

public class StaticImportMethodFix extends StaticImportMemberFix<PsiMethod> {
  private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCall;

  public StaticImportMethodFix(@Nonnull PsiMethodCallExpression methodCallExpression) {
    myMethodCall = SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
  }

  @Nonnull
  @Override
  protected LocalizeValue getBaseText() {
    return JavaQuickFixLocalize.staticImportMethodText();
  }

  @Nonnull
  @Override
  protected String getMemberPresentableText(PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_FQ_NAME, 0);
  }

  @Nonnull
  @Override
  protected List<PsiMethod> getMembersToImport(boolean applicableOnly) {
    final Project project = myMethodCall.getProject();
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    final PsiMethodCallExpression element = myMethodCall.getElement();
    PsiReferenceExpression reference = element == null ? null : element.getMethodExpression();
    String name = reference == null ? null : reference.getReferenceName();
    if (name == null) {
      return Collections.emptyList();
    }
    final StaticMembersProcessor<PsiMethod> processor = new MyStaticMethodProcessor(element);
    cache.processMethodsWithName(name, element.getResolveScope(), processor);
    return processor.getMembersToImport(applicableOnly);
  }

  @Nonnull
  protected StaticImportMethodQuestionAction<PsiMethod> createQuestionAction(List<PsiMethod> methodsToImport, @Nonnull Project project, Editor editor) {
    return new StaticImportMethodQuestionAction<>(project, editor, methodsToImport, myMethodCall);
  }

  @Nullable
  @Override
  protected PsiElement getElement() {
    return myMethodCall.getElement();
  }

  @Nullable
  @Override
  protected PsiElement getQualifierExpression() {
    final PsiMethodCallExpression element = myMethodCall.getElement();
    return element != null ? element.getMethodExpression().getQualifierExpression() : null;
  }

  @Nullable
  @Override
  protected PsiElement resolveRef() {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) getElement();
    return methodCallExpression != null ? methodCallExpression.resolveMethod() : null;
  }

  private static class MyStaticMethodProcessor extends StaticMembersProcessor<PsiMethod> {

    private MyStaticMethodProcessor(PsiMethodCallExpression place) {
      super(place);
    }

    @Override
    protected boolean isApplicable(PsiMethod method, PsiElement place) {
      final PsiExpressionList argumentList = ((PsiMethodCallExpression) place).getArgumentList();
      final MethodCandidateInfo candidateInfo = new MethodCandidateInfo(method, PsiSubstitutor.EMPTY, false, false, argumentList, null, argumentList.getExpressionTypes(), null);
      PsiSubstitutor substitutorForMethod = candidateInfo.getSubstitutor();
      if (PsiUtil.isApplicable(method, substitutorForMethod, argumentList)) {
        final PsiType returnType = substitutorForMethod.substitute(method.getReturnType());
        final PsiType expectedType = getExpectedType();
        return expectedType == null || returnType == null || TypeConversionUtil.isAssignable(expectedType, returnType);
      }
      return false;
    }
  }
}
