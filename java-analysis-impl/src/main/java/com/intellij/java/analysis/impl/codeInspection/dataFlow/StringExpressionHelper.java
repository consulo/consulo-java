// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import consulo.util.lang.Pair;
import consulo.language.psi.ElementManipulators;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiCall;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiConstantEvaluationHelper;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import consulo.language.psi.PsiReference;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiReturnStatement;
import com.intellij.java.language.psi.PsiVariable;
import consulo.content.scope.SearchScope;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.application.util.function.CommonProcessors;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class StringExpressionHelper {

  public static @Nullable Pair<PsiElement, String> evaluateExpression(PsiElement expression) {
    return evaluateExpression(expression, new HashSet<>());
  }

  public static @Nullable Pair<PsiElement, String> evaluateExpression(PsiElement expression, Collection<PsiElement> visited) {
    if (!visited.add(expression)) {
      return null;
    }

    if (expression instanceof PsiLiteralExpression) {
      return evaluatePsiLiteralExpression(expression);
    }

    if (expression instanceof PsiReferenceExpression) {
      PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
      if (resolve instanceof PsiVariable) {
        PsiExpression initializer = ((PsiVariable)resolve).getInitializer();
        if (initializer != null) {
          Pair<PsiElement, String> expr = evaluateExpression(initializer, visited);
          if (expr != null) return expr;
        }
      }
    }

    if (expression instanceof PsiMethodCallExpression) {
      final PsiElement element = ((PsiMethodCallExpression)expression).getMethodExpression().resolve();
      if (element instanceof PsiMethod) {
        PsiCodeBlock body = ((PsiMethod)element).getBody();
        if (body != null) {
          final Set<PsiExpression> returns = new HashSet<>();

          body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitClass(PsiClass aClass) {}

            @Override
            public void visitLambdaExpression(PsiLambdaExpression expression) {}

            @Override
            public void visitReturnStatement(PsiReturnStatement statement) {
              PsiExpression returnValue = statement.getReturnValue();
              if (returnValue != null) {
                returns.add(returnValue);
              }
            }
          });

          for (PsiExpression psiExpression : returns) {
            Pair<PsiElement, String> pair = evaluateExpression(psiExpression, visited);
            if (pair != null) {
              return pair;
            }
          }
        }

        return evaluateExpression(element, visited);
      }
      return null;
    }

    Pair<PsiElement, String> constantExpression = evaluateConstantExpression(expression);
    if (constantExpression != null) {
      return constantExpression;
    }

    if (expression instanceof PsiBinaryExpression binaryExpression) {
      // a="a"; b="b"  return a+b;
      return evaluatePolyadicExpressions(expression, visited, binaryExpression.getLOperand(), binaryExpression.getROperand());
    }
    if (expression instanceof PsiPolyadicExpression) {
      // a="a"; b="b"; c="c"  return a+b+c;
      return evaluatePolyadicExpressions(expression, visited, ((PsiPolyadicExpression)expression).getOperands());
    }


    Collection<? extends PsiElement> elements = DfaUtil.getPossibleInitializationElements(expression);
    for (PsiElement element : elements) {
      Pair<PsiElement, String> expr = evaluateExpression(element, visited);
      if (expr != null) return expr;
    }

    return null;
  }

  private static @Nullable Pair<PsiElement, String> evaluatePolyadicExpressions(PsiElement expression,
                                                                                Collection<PsiElement> visited,
                                                                                PsiExpression ... operands) {
    StringBuilder sb = new StringBuilder();
    for (PsiExpression operand : operands) {
      Pair<PsiElement, String> pair = evaluateExpression(operand, visited);
      if (pair == null) return null;
      sb.append(pair.second);
    }
    return Pair.create(expression, sb.toString());
  }

  private static Pair<PsiElement, String> evaluatePsiLiteralExpression(PsiElement expression) {
    return Pair.create(expression, ElementManipulators.getValueText(expression));
  }

  public static @Nullable Pair<PsiElement, String> evaluateConstantExpression(PsiElement expression) {
    PsiConstantEvaluationHelper helper = JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper();
    Object result = helper.computeConstantExpression(expression);
    if (result instanceof String) {
      return Pair.create(expression, (String)result);
    }
    return null;
  }

  public static Set<Pair<PsiElement, String>> searchStringExpressions(final PsiMethod psiMethod,
                                                                               SearchScope searchScope,
                                                                               int expNum) {
    Set<Pair<PsiElement, String>> pairs = new HashSet<>();
    for (PsiCall methodCallExpression : searchMethodCalls(psiMethod, searchScope)) {
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (argumentList == null) continue;
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length > expNum) {
        final PsiExpression expression = expressions[expNum];
        Pair<PsiElement, String> pair = evaluateExpression(expression);
        if (pair != null) {
          pairs.add(pair);
        }
      }
    }

    return pairs;
  }

  public static Set<PsiCall> searchMethodCalls(final PsiMethod psiMethod, SearchScope searchScope) {
    final Set<PsiCall> callExpressions = new HashSet<>();
    final CommonProcessors.CollectUniquesProcessor<PsiReference> consumer = new CommonProcessors.CollectUniquesProcessor<>();

    MethodReferencesSearch.search(psiMethod, searchScope, true).forEach(consumer);

    for (PsiReference psiReference : consumer.getResults()) {
      final PsiCall methodCallExpression = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiCall.class);

      if (methodCallExpression != null) {
        callExpressions.add(methodCallExpression);
      }
    }


    return callExpressions;
  }
}
