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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class WrapArrayToArraysAsListFix extends MethodArgumentFix {
  public static final ArgumentFixerActionFactory REGISTAR = new MyFixerActionFactory();

  protected WrapArrayToArraysAsListFix(final @jakarta.annotation.Nonnull PsiExpressionList list,
                                       final int i,
                                       final @Nonnull PsiType toType,
                                       final @jakarta.annotation.Nonnull ArgumentFixerActionFactory fixerActionFactory) {
    super(list, i, toType, fixerActionFactory);
  }

  @Nonnull
  @Override
  public String getText() {
    if (myArgList.getExpressions().length == 1) {
      return JavaQuickFixBundle.message("wrap.array.to.arrays.as.list.single.parameter.text");
    } else {
      return JavaQuickFixBundle.message("wrap.array.to.arrays.as.list.parameter.text", myIndex + 1);
    }
  }

  public static class MyFixerActionFactory extends ArgumentFixerActionFactory {

    @jakarta.annotation.Nullable
    @Override
    protected PsiExpression getModifiedArgument(final PsiExpression expression,
                                                final PsiType toType) throws IncorrectOperationException {
      final PsiType exprType = expression.getType();
      if (!(exprType instanceof PsiArrayType && toType instanceof PsiClassType)) {
        return null;
      }
      final PsiClass resolvedToType = ((PsiClassType) toType).resolve();
      if (resolvedToType == null) {
        return null;
      }
      final PsiClass javaUtilList = getJavaUtilList(expression);
      if (javaUtilList == null || !InheritanceUtil.isInheritorOrSelf(javaUtilList, resolvedToType, true)) {
        return null;
      }
      final PsiType[] parameters = ((PsiClassType) toType).getParameters();
      final PsiType arrayComponentType = ((PsiArrayType) exprType).getComponentType();
      if (!(parameters.length == 1 && parameters[0].equals(arrayComponentType))) {
        return null;
      }

      final String rawNewExpression = String.format("java.util.Arrays.asList(%s)", expression.getText());
      final Project project = expression.getProject();
      final PsiExpression newExpression = JavaPsiFacade.getInstance(project).getElementFactory()
          .createExpressionFromText(rawNewExpression, null);
      return (PsiExpression) JavaCodeStyleManager.getInstance(project).shortenClassReferences(newExpression);
    }

    @Nullable
    private static PsiClass getJavaUtilList(final PsiElement context) {
      return JavaPsiFacade.getInstance(context.getProject()).findClass(JavaClassNames.JAVA_UTIL_LIST,
          context.getResolveScope());
    }

    @Override
    public boolean areTypesConvertible(final PsiType exprType,
                                       final PsiType parameterType,
                                       final PsiElement context) {
      return true;
    }

    @Override
    public MethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new WrapArrayToArraysAsListFix(list, i, toType, this);
    }
  }
}
