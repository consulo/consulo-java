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

/*
 * User: anna
 * Date: 24-Oct-2008
 */
package com.intellij.java.analysis.impl.refactoring.util.duplicates;

import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nullable;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiStatement;
import consulo.language.codeStyle.CodeStyleManager;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.util.IncorrectOperationException;

public abstract class GotoReturnValue implements ReturnValue {
  @Nullable
  public PsiStatement createReplacement(final PsiMethod extractedMethod, final PsiMethodCallExpression methodCallExpression) throws
                                                                                                                             IncorrectOperationException {
    if (!TypeConversionUtil.isBooleanType(extractedMethod.getReturnType())) return null;
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
    final PsiIfStatement statement = (PsiIfStatement)elementFactory.createStatementFromText(getGotoStatement(), null);
    final PsiExpression condition = statement.getCondition();
    assert condition != null;
    condition.replace(methodCallExpression);
    return (PsiStatement)CodeStyleManager.getInstance(statement.getManager().getProject()).reformat(statement);
  }

  @NonNls
  public abstract String getGotoStatement();
}