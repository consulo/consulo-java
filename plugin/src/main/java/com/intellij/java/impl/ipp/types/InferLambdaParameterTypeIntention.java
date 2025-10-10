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
package com.intellij.java.impl.ipp.types;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.InferLambdaParameterTypeIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class InferLambdaParameterTypeIntention extends Intention {
  private static final Logger LOG = Logger.getInstance(InferLambdaParameterTypeIntention.class);
  private String myInferredTypesText;

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new LambdaParametersPredicate();
  }

  @Nonnull
  @Override
  public LocalizeValue getText() {
    return LocalizeValue.localizeTODO("Expand lambda to " + myInferredTypesText + " -> {...}");
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
    LOG.assertTrue(lambdaExpression != null);
    final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
    final String buf = getInferredTypes(functionalInterfaceType, lambdaExpression);
    lambdaExpression.getParameterList().replace(JavaPsiFacade.getElementFactory(element.getProject()).createMethodFromText("void foo" + buf,
                                                                                                                           element).getParameterList());
  }

  @Nullable
  private static String getInferredTypes(PsiType functionalInterfaceType, final PsiLambdaExpression lambdaExpression) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final StringBuilder buf = new StringBuilder();
    buf.append("(");
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    LOG.assertTrue(interfaceMethod != null);
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
    final PsiParameter[] lambdaParameters = lambdaExpression.getParameterList().getParameters();
    if (parameters.length != lambdaParameters.length) return null;
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiType psiType = GenericsUtil.eliminateWildcards(LambdaUtil.getSubstitutor(interfaceMethod, resolveResult).substitute(parameter.getType()));
      if (psiType != null) {
        buf.append(psiType.getPresentableText()).append(" ").append(lambdaParameters[i].getName());
      }
      else {
        buf.append(lambdaParameters[i].getName());
      }
      if (i < parameters.length - 1) {
        buf.append(", ");
      }
    }
    buf.append(")");
    return buf.toString();
  }


  private class LambdaParametersPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      if (lambdaExpression != null) {
        PsiParameter[] parameters = lambdaExpression.getParameterList().getParameters();
        if (parameters.length == 0) return false;
        for (PsiParameter parameter : parameters) {
          if (parameter.getTypeElement() != null) {
            return false;
          }
        }
        if (PsiTreeUtil.isAncestor(lambdaExpression.getParameterList(), element, false)) {
          final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
          if (functionalInterfaceType != null && LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType) != null && LambdaUtil.isLambdaFullyInferred(lambdaExpression, functionalInterfaceType)) {
            myInferredTypesText = getInferredTypes(functionalInterfaceType, lambdaExpression);
            return myInferredTypesText != null;
          }
        }
      }
      return false;
    }
  }
}
