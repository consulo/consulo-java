/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User: anna
 * Date: 2/17/12
 */
public class AddTypeArgumentsConditionalFix implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance(AddTypeArgumentsConditionalFix.class);

  private final PsiSubstitutor mySubstitutor;
  private final PsiMethodCallExpression myExpression;
  private final PsiMethod myMethod;

  public AddTypeArgumentsConditionalFix(PsiSubstitutor substitutor,
                                        PsiMethodCallExpression expression,
                                        PsiMethod method) {
    mySubstitutor = substitutor;
    myExpression = expression;
    myMethod = method;
  }

  @Nonnull
  @Override
  public String getText() {
    return "Add explicit type arguments";
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (mySubstitutor.isValid() && myExpression.isValid() && myMethod.isValid()) {
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiTypeParameter[] typeParameters = myMethod.getTypeParameters();
    final String typeArguments = "<" + StringUtil.join(typeParameters, parameter -> {
      final PsiType substituteTypeParam = mySubstitutor.substitute(parameter);
      LOG.assertTrue(substituteTypeParam != null);
      return GenericsUtil.eliminateWildcards(substituteTypeParam).getCanonicalText();
    }, ", ") + ">";
    final PsiExpression expression = myExpression.getMethodExpression().getQualifierExpression();
    String withTypeArgsText;
    if (expression != null) {
      withTypeArgsText = expression.getText();
    } else {
      if (isInStaticContext(myExpression, null) || myMethod.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiClass aClass = myMethod.getContainingClass();
        LOG.assertTrue(aClass != null);
        withTypeArgsText = aClass.getQualifiedName();
      } else {
        withTypeArgsText = "this";
      }
    }
    withTypeArgsText += "." + typeArguments + myExpression.getMethodExpression().getReferenceName();
    final PsiExpression withTypeArgs = JavaPsiFacade.getElementFactory(project).createExpressionFromText
        (withTypeArgsText + myExpression.getArgumentList().getText(), myExpression);
    myExpression.replace(withTypeArgs);
  }

  public static boolean isInStaticContext(PsiElement element, @Nullable final PsiClass aClass) {
    return PsiUtil.getEnclosingStaticElement(element, aClass) != null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static void register(HighlightInfo highlightInfo, PsiExpression expression, @Nonnull PsiType lType) {
    if (lType != PsiType.NULL && expression instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression) expression).getThenExpression();
      final PsiExpression elseExpression = ((PsiConditionalExpression) expression).getElseExpression();
      if (thenExpression != null && elseExpression != null) {
        final PsiType thenType = thenExpression.getType();
        final PsiType elseType = elseExpression.getType();
        if (thenType != null && elseType != null) {
          final boolean thenAssignable = TypeConversionUtil.isAssignable(lType, thenType);
          final boolean elseAssignable = TypeConversionUtil.isAssignable(lType, elseType);
          if (!thenAssignable && thenExpression instanceof PsiMethodCallExpression) {
            inferTypeArgs(highlightInfo, lType, thenExpression);
          }
          if (!elseAssignable && elseExpression instanceof PsiMethodCallExpression) {
            inferTypeArgs(highlightInfo, lType, elseExpression);
          }
        }
      }
    }
  }

  private static void inferTypeArgs(HighlightInfo highlightInfo, PsiType lType, PsiExpression thenExpression) {
    final JavaResolveResult result = ((PsiMethodCallExpression) thenExpression).resolveMethodGenerics();
    final PsiMethod method = (PsiMethod) result.getElement();
    if (method != null) {
      final PsiType returnType = method.getReturnType();
      final PsiClass aClass = method.getContainingClass();
      if (returnType != null && aClass != null && aClass.getQualifiedName() != null) {
        final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(method.getProject());
        final PsiDeclarationStatement variableDeclarationStatement = javaPsiFacade.getElementFactory()
            .createVariableDeclarationStatement("xxx", lType, thenExpression);
        final PsiExpression initializer = ((PsiLocalVariable) variableDeclarationStatement.getDeclaredElements
            ()[0]).getInitializer();
        LOG.assertTrue(initializer != null);

        final PsiSubstitutor substitutor = javaPsiFacade.getResolveHelper().inferTypeArguments(method
                .getTypeParameters(), method.getParameterList().getParameters(),
            ((PsiMethodCallExpression) thenExpression).getArgumentList().getExpressions(),
            PsiSubstitutor.EMPTY, initializer, DefaultParameterTypeInferencePolicy.INSTANCE);
        PsiType substitutedType = substitutor.substitute(returnType);
        if (substitutedType != null && TypeConversionUtil.isAssignable(lType, substitutedType)) {
          QuickFixAction.registerQuickFixAction(highlightInfo, thenExpression.getTextRange(),
              new AddTypeArgumentsConditionalFix(substitutor, (PsiMethodCallExpression) thenExpression,
                  method));
        }
      }
    }
  }
}
