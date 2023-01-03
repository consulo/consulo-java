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
package com.intellij.java.impl.ipp.trivialif;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import javax.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.SplitElseIfIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class SplitElseIfIntention extends Intention {

  @Nonnull
  public PsiElementPredicate getElementPredicate() {
    return new SplitElseIfPredicate();
  }

  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiIfStatement parentStatement =
      (PsiIfStatement)token.getParent();
    if (parentStatement == null) {
      return;
    }
    final PsiStatement elseBranch = parentStatement.getElseBranch();
    if (elseBranch == null) {
      return;
    }
    final String newStatement = '{' + elseBranch.getText() + '}';
    replaceStatement(newStatement, elseBranch);
  }
}