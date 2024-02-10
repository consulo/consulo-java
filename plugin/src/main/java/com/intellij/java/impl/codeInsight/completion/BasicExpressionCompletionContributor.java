/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.analysis.codeInsight.guess.GuessManager;
import com.intellij.java.impl.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.java.impl.codeInsight.lookup.KeywordLookupItem;
import com.intellij.java.impl.codeInsight.lookup.VariableLookupItem;
import com.intellij.java.impl.codeInsight.template.SmartCompletionContextType;
import com.intellij.java.impl.psi.filters.getters.ClassLiteralGetter;
import com.intellij.java.impl.psi.filters.getters.ThisGetter;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateSettings;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.BaseScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Consumer;

/**
 * @author peter
 */
public class BasicExpressionCompletionContributor {

  private static void addKeyword(final Consumer<LookupElement> result, final PsiElement element, final String s) {
    result.accept(createKeywordLookupItem(element, s));
  }

  public static LookupElement createKeywordLookupItem(final PsiElement element, final String s) {
    return new KeywordLookupItem(JavaPsiFacade.getElementFactory(element.getProject()).createKeyword(s, element), element);
  }

  public static void fillCompletionVariants(JavaSmartCompletionParameters parameters, final Consumer<LookupElement> result, PrefixMatcher matcher) {
    final PsiElement element = parameters.getPosition();
    if (JavaKeywordCompletion.isAfterTypeDot(element)) {
      addKeyword(result, element, PsiKeyword.CLASS);
      addKeyword(result, element, PsiKeyword.THIS);

    }

    if (!JavaKeywordCompletion.AFTER_DOT.accepts(element)) {
      if (parameters.getParameters().getInvocationCount() <= 1) {
        new CollectionsUtilityMethodsProvider(parameters.getPosition(), parameters.getExpectedType(), parameters.getDefaultType(), result).addCompletions(StringUtil.isNotEmpty(matcher
            .getPrefix()));
      }
      ClassLiteralGetter.addCompletions(parameters, result, matcher);

      final PsiElement position = parameters.getPosition();

      final PsiType expectedType = parameters.getExpectedType();

      for (final Template template : TemplateSettings.getInstance().getTemplates()) {
        if (!template.isDeactivated() && template.getTemplateContext().isEnabled(new SmartCompletionContextType())) {
          result.accept(new SmartCompletionTemplateItem(template, position));
        }
      }

      addKeyword(result, position, PsiKeyword.TRUE);
      addKeyword(result, position, PsiKeyword.FALSE);

      final PsiElement parent = position.getParent();
      if (parent != null && !(parent.getParent() instanceof PsiSwitchLabelStatement)) {
        for (final PsiExpression expression : ThisGetter.getThisExpressionVariants(position)) {
          result.accept(new ExpressionLookupItem(expression));
        }
      }

      processDataflowExpressionTypes(parameters, expectedType, matcher, result);
    }
  }

  static void processDataflowExpressionTypes(JavaSmartCompletionParameters parameters, @Nullable PsiType expectedType, final PrefixMatcher matcher, Consumer<? super LookupElement> consumer) {
    final PsiExpression context = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiExpression.class);
    if (context == null) {
      return;
    }

    MultiMap<PsiExpression, PsiType> map = GuessManager.getInstance(context.getProject()).getControlFlowExpressionTypes(context, parameters.getParameters().getInvocationCount() > 1);
    if (map.isEmpty()) {
      return;
    }

    PsiScopesUtil.treeWalkUp(new BaseScopeProcessor() {
      @Override
      public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
        if (element instanceof PsiLocalVariable) {
          if (!matcher.prefixMatches(((PsiLocalVariable) element).getName())) {
            return true;
          }

          final PsiExpression expression = ((PsiLocalVariable) element).getInitializer();
          if (expression instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression) expression;
            final PsiExpression operand = typeCastExpression.getOperand();
            if (operand != null) {
              if (map.get(operand).contains(typeCastExpression.getType())) {
                map.remove(operand);
              }
            }
          }
        }
        return true;
      }
    }, context, context.getContainingFile());

    for (PsiExpression expression : map.keySet()) {
      for (PsiType castType : map.get(expression)) {
        PsiType baseType = expression.getType();
        if (expectedType == null || (expectedType.isAssignableFrom(castType) && (baseType == null || !expectedType.isAssignableFrom(baseType)))) {
          consumer.accept(CastingLookupElementDecorator.createCastingElement(expressionToLookupElement(expression), castType));
        }
      }
    }
  }

  @Nonnull
  private static LookupElement expressionToLookupElement(@Nonnull PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression) expression;
      if (!refExpr.isQualified()) {
        final PsiElement target = refExpr.resolve();
        if (target instanceof PsiVariable) {
          final VariableLookupItem item = new VariableLookupItem((PsiVariable) target);
          item.setSubstitutor(PsiSubstitutor.EMPTY);
          return item;
        }
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
      if (!call.getMethodExpression().isQualified()) {
        final PsiMethod method = call.resolveMethod();
        if (method != null) {
          return new JavaMethodCallElement(method);
        }
      }
    }

    return new ExpressionLookupItem(expression);
  }

}
