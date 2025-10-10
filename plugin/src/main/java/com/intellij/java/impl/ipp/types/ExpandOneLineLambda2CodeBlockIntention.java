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
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ExpandOneLineLambda2CodeBlockIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class ExpandOneLineLambda2CodeBlockIntention extends Intention {
  private static final Logger LOG = Logger.getInstance(ExpandOneLineLambda2CodeBlockIntention.class);
  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new LambdaExpressionPredicate();
  }

  @Nonnull
  @Override
  public LocalizeValue getText() {
    return LocalizeValue.localizeTODO("Expand lambda expression body to {...}");
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
    LOG.assertTrue(lambdaExpression != null);
    final PsiElement body = lambdaExpression.getBody();
    LOG.assertTrue(body instanceof PsiExpression);
    String blockText = "{";
    blockText += PsiType.VOID.equals(((PsiExpression) body).getType()) ? "" : "return ";
    blockText +=  body.getText() + ";}";

    final String resultedLambdaText = lambdaExpression.getParameterList().getText() + "->" + blockText;
    final PsiExpression expressionFromText =
      JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText(resultedLambdaText, lambdaExpression);
    lambdaExpression.replace(expressionFromText);
  }

  

  private static class LambdaExpressionPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      if (lambdaExpression != null) {
        return lambdaExpression.getBody() instanceof PsiExpression;
      }
      return false;
    }
  }
}
