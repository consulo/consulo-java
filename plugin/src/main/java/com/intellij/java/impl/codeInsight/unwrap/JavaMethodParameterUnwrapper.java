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
package com.intellij.java.impl.codeInsight.unwrap;

import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

import java.util.List;

public class JavaMethodParameterUnwrapper extends JavaUnwrapper {
  private static final Logger LOG = Logger.getInstance(JavaMethodParameterUnwrapper.class);

  public JavaMethodParameterUnwrapper() {
    super("");
  }

  @Override
  @RequiredReadAction
  public String getDescription(PsiElement e) {
    String text = e.getText();
    if (text.length() > 20) text = text.substring(0, 17) + "...";
    return CodeInsightLocalize.unwrapWithPlaceholder(text).get();
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
    final PsiElement parent = e.getParent();
    if (e instanceof PsiExpression){
      if (parent instanceof PsiExpressionList) {
        return true;
      }
      if (e instanceof PsiReferenceExpression && parent instanceof PsiCallExpression callExpression) {
        final PsiExpressionList argumentList = callExpression.getArgumentList();
        if (argumentList != null && argumentList.getExpressions().length == 1) {
          return true;
        }
      }
    } else if (e instanceof PsiJavaCodeReferenceElement) {
      if (parent instanceof PsiCall call) {
        final PsiExpressionList argumentList = call.getArgumentList();
        if (argumentList != null && argumentList.getExpressions().length == 1) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return isTopLevelCall(e) ? e.getParent() : e.getParent().getParent();
  }

  private static boolean isTopLevelCall(PsiElement e) {
    return e instanceof PsiReferenceExpression && e.getParent() instanceof PsiCallExpression
      || e instanceof PsiJavaCodeReferenceElement && !(e instanceof PsiExpression);
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiElement methodCall = isTopLevelCall(element) ? element.getParent() : element.getParent().getParent();
    final PsiElement extractedElement = isTopLevelCall(element) ? getArg(element) : element;
    context.extractElement(extractedElement, methodCall);
    if (methodCall.getParent() instanceof PsiExpressionList) {
      context.delete(methodCall);
    }
    else {
      context.deleteExactly(methodCall);
    }
  }

  private static PsiExpression getArg(PsiElement element) {
    final PsiExpressionList argumentList = ((PsiCall)element.getParent()).getArgumentList();
    LOG.assertTrue(argumentList != null);
    final PsiExpression[] args = argumentList.getExpressions();
    LOG.assertTrue(args.length == 1);
    return args[0];
  }
}
