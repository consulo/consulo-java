/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.asserttoif;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiAssertStatement;
import com.intellij.java.language.psi.PsiExpression;
import com.siyeh.ig.psiutils.BoolUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.AssertToIfIntention", fileExtensions = "java", categories = {
		"Java",
		"Other"
})
public class AssertToIfIntention extends Intention {

  @Override
  @Nonnull
  protected PsiElementPredicate getElementPredicate() {
    return new AssertStatementPredicate();
  }

  @Override
  public void processIntention(@Nonnull PsiElement element)
    throws IncorrectOperationException {
    final PsiAssertStatement assertStatement = (PsiAssertStatement)element;
    final PsiExpression condition = assertStatement.getAssertCondition();
    final PsiExpression description =
      assertStatement.getAssertDescription();
    final String negatedConditionString =
      BoolUtils.getNegatedExpressionText(condition);
    @NonNls final String newStatement;
    if (description == null) {
      newStatement = "if(" + negatedConditionString +
                     "){ throw new java.lang.AssertionError();}";
    }
    else {
      newStatement = "if(" + negatedConditionString +
                     "){ throw new java.lang.AssertionError(" +
                     description.getText() + ");}";
    }
    replaceStatement(newStatement, assertStatement);
  }
}