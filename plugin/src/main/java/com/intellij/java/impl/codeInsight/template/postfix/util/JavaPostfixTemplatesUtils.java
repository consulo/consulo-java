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
package com.intellij.java.impl.codeInsight.template.postfix.util;

import com.intellij.java.impl.codeInsight.CodeInsightServicesUtil;
import com.intellij.java.impl.refactoring.util.CommonJavaRefactoringUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.postfixTemplate.PostfixTemplatePsiInfo;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateExpressionSelector;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateExpressionSelectorBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Predicates;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class JavaPostfixTemplatesUtils {
  private JavaPostfixTemplatesUtils() {
  }

  public static PostfixTemplatePsiInfo JAVA_PSI_INFO = new PostfixTemplatePsiInfo() {
    @Nonnull
    @Override
    public PsiElement createExpression(@Nonnull PsiElement context,
                                       @Nonnull String prefix,
                                       @Nonnull String suffix) {
      return JavaPostfixTemplatesUtils.createExpression(context, prefix, suffix);
    }

    @Nonnull
    @Override
    public PsiExpression getNegatedExpression(@Nonnull PsiElement element) {
      return CodeInsightServicesUtil.invertCondition((PsiExpression)element);
    }
  };

  public static Predicate<PsiElement> IS_BOOLEAN =
    element -> element instanceof PsiExpression && isBoolean(((PsiExpression)element).getType());

  public static Predicate<PsiElement> IS_THROWABLE =
    element -> element instanceof PsiExpression && isThrowable((((PsiExpression)element).getType()));

  public static Predicate<PsiElement> IS_NON_VOID =
    element -> element instanceof PsiExpression && isNonVoid((((PsiExpression)element).getType()));

  public static Predicate<PsiElement> IS_NOT_PRIMITIVE =
    element -> element instanceof PsiExpression && isNotPrimitiveTypeExpression(((PsiExpression)element));

  public static PsiElement createStatement(@Nonnull PsiElement context,
                                           @Nonnull String prefix,
                                           @Nonnull String suffix) {
    PsiExpression expr = getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    assert parent instanceof PsiStatement;
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    return factory.createStatementFromText(prefix + expr.getText() + suffix, expr);
  }

  public static PsiElement createExpression(@Nonnull PsiElement context,
                                            @Nonnull String prefix,
                                            @Nonnull String suffix) {
    PsiExpression expr = getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    assert parent instanceof PsiStatement;
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    return factory.createExpressionFromText(prefix + expr.getText() + suffix, expr);
  }

  @Contract("null -> false")
  public static boolean isNotPrimitiveTypeExpression(@Nullable PsiExpression expression) {
    return expression != null && !(expression.getType() instanceof PsiPrimitiveType);
  }

  @Contract("null -> false")
  public static boolean isIterable(@Nullable PsiType type) {
    return type != null && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE);
  }

  @Contract("null -> false")
  public static boolean isThrowable(@Nullable PsiType type) {
    return type != null && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_THROWABLE);
  }

  @Contract("null -> false")
  public static boolean isArray(@Nullable PsiType type) {
    return type != null && type instanceof PsiArrayType;
  }

  @Contract("null -> false")
  public static boolean isBoolean(@Nullable PsiType type) {
    return type != null && (PsiType.BOOLEAN.equals(type) || PsiType.BOOLEAN.equals(PsiPrimitiveType.getUnboxedType(type)));
  }

  @Contract("null -> false")
  public static boolean isNonVoid(@Nullable PsiType type) {
    return type != null && !PsiType.VOID.equals(type);
  }

  @Contract("null -> false")
  public static boolean isNumber(@Nullable PsiType type) {
    if (type == null) {
      return false;
    }
    if (PsiType.INT.equals(type) || PsiType.BYTE.equals(type) || PsiType.LONG.equals(type)) {
      return true;
    }

    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(type);
    return PsiType.INT.equals(unboxedType) || PsiType.BYTE.equals(unboxedType) || PsiType.LONG.equals(unboxedType);
  }

  @Nullable
  public static PsiExpression getTopmostExpression(PsiElement context) {
    PsiExpressionStatement statement = PsiTreeUtil.getNonStrictParentOfType(context, PsiExpressionStatement.class);
    return statement != null ? PsiTreeUtil.getChildOfType(statement, PsiExpression.class) : null;
  }

  public static void formatPsiCodeBlock(PsiElement newStatement, Editor editor) {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(newStatement.getProject());
    PsiElement statement = newStatement.replace(codeStyleManager.reformat(newStatement));

    PsiCodeBlock type = PsiTreeUtil.getChildOfType(statement, PsiCodeBlock.class);
    assert type != null;
    PsiCodeBlock block = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(type);
    TextRange range = block.getStatements()[0].getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    editor.getCaretModel().moveToOffset(range.getStartOffset());
  }

  public static PostfixTemplateExpressionSelector selectorTopmost(Predicate<? super PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@Nonnull PsiElement context, @Nonnull Document document, int offset) {
        return ContainerUtil.createMaybeSingletonList(getTopmostExpression(context));
      }

      @Override
      protected Predicate<PsiElement> getFilters(int offset) {
        return Predicates.and(super.getFilters(offset), getPsiErrorFilter());
      }

      @Nonnull
      @Override
      public Function<PsiElement, String> getRenderer() {
        return JavaPostfixTemplatesUtils.getRenderer();
      }
    };
  }

  @Nonnull
  public static PostfixTemplateExpressionSelector selectorAllExpressionsWithCurrentOffset(@Nullable Predicate<? super PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@Nonnull PsiElement context, @Nonnull Document document, int offset) {
        return new ArrayList<>(CommonJavaRefactoringUtil.collectExpressions(context.getContainingFile(), document,
                                                                            Math.max(offset - 1, 0), false));
      }

      @Nonnull
      @Override
      public List<PsiElement> getExpressions(@Nonnull PsiElement context, @Nonnull Document document, int offset) {
        List<PsiElement> expressions = super.getExpressions(context, document, offset);
        if (!expressions.isEmpty()) return expressions;

        return ContainerUtil.filter(ContainerUtil.<PsiElement>createMaybeSingletonList(getTopmostExpression(context)), getFilters(offset));
      }

      @Nonnull
      @Override
      public Function<PsiElement, String> getRenderer() {
        return JavaPostfixTemplatesUtils.getRenderer();
      }
    };
  }

  @Nonnull
  public static Function<PsiElement, String> getRenderer() {
    return element -> {
      assert element instanceof PsiExpression;
      return PsiExpressionTrimRenderer.render((PsiExpression)element);
    };
  }
}

