/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.braces;

import javax.annotation.Nonnull;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.PsiElementPredicate;

public class AddBracesIntention extends BaseBracesIntention {

  @Nonnull
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        final PsiStatement statement = getSurroundingStatement(element);
        return statement != null && !(statement instanceof PsiBlockStatement);
      }
    };
  }

  @Nonnull
  @Override
  protected String getMessageKey() {
    return "add.braces.intention.name";
  }

  protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
    final PsiStatement statement = getSurroundingStatement(element);
    if (statement == null) {
      return;
    }
    final String newStatement = "{\n" + statement.getText() + "\n}";
    replaceStatement(newStatement, statement);
  }
}