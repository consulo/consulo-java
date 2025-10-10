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

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.language.psi.*;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

public class CastMethodArgumentFix extends MethodArgumentFix {
  private CastMethodArgumentFix(PsiExpressionList list, int i, PsiType toType, final ArgumentFixerActionFactory factory) {
    super(list, i, toType, factory);
  }

  @Override
  @Nonnull
  public LocalizeValue getText() {
    if (myArgList.getExpressions().length == 1) {
      return JavaQuickFixLocalize.castSingleParameterText(JavaHighlightUtil.formatType(myToType));
    }

    return JavaQuickFixLocalize.castParameterText(myIndex + 1, JavaHighlightUtil.formatType(myToType));
  }

  private static class MyFixerActionFactory extends ArgumentFixerActionFactory {
    @Override
    public CastMethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new CastMethodArgumentFix(list, i, toType, this);
    }

    @Override
    protected PsiExpression getModifiedArgument(final PsiExpression expression, PsiType toType) throws IncorrectOperationException {
      final PsiType exprType = expression.getType();
      if (exprType instanceof PsiClassType && toType instanceof PsiPrimitiveType) {
        toType = ((PsiPrimitiveType)toType).getBoxedType(expression);
        assert toType != null;
      }
      return AddTypeCastFix.createCastExpression(expression, expression.getProject(), toType);
    }

    @Override
    public boolean areTypesConvertible(PsiType exprType, PsiType parameterType, final PsiElement context) {
      if (exprType instanceof PsiClassType && parameterType instanceof PsiPrimitiveType) {
        parameterType = ((PsiPrimitiveType)parameterType).getBoxedType(context); //unboxing from type of cast expression will take place at runtime
        if (parameterType == null) return false;
      }
      return parameterType.isConvertibleFrom(exprType);
    }
  }

  public static final ArgumentFixerActionFactory REGISTRAR = new MyFixerActionFactory();
}
