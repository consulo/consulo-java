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
package com.intellij.java.impl.ipp.exceptions;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;
import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceArmWithTryFinallyIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ReplaceArmWithTryFinallyIntention
  extends Intention {

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new AutomaticResourceManagementPredicate();
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element)
    throws IncorrectOperationException {
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiTryStatement tryStatement =
      (PsiTryStatement)token.getParent();
    if (tryStatement == null) {
      return;
    }
    final boolean replaceAll = tryStatement.getCatchBlocks().length == 0;
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList == null) {
      return;
    }
    final List<PsiResourceVariable> resourceVariables =
      resourceList.getResourceVariables();
    final StringBuilder newTryStatement = new StringBuilder("{");
    for (PsiResourceVariable resourceVariable : resourceVariables) {
      newTryStatement.append(resourceVariable.getText());
      newTryStatement.append(";\ntry {");
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return;
    }
    final PsiElement[] children = tryBlock.getChildren();
    for (int i = 1; i < children.length - 1; i++) {
      final PsiElement child = children[i];
      newTryStatement.append(child.getText());
    }
    final int resourceVariablesSize = resourceVariables.size();
    for (int i = resourceVariablesSize - 1; i >= 0; i--) {
      final PsiResourceVariable resourceVariable =
        resourceVariables.get(i);
      newTryStatement.append("} finally {\n");
      newTryStatement.append(resourceVariable.getName());
      newTryStatement.append(".close();\n}");
    }
    newTryStatement.append('}');
    final PsiElementFactory factory =
      JavaPsiFacade.getElementFactory(element.getProject());
    final PsiCodeBlock newCodeBlock =
      factory.createCodeBlockFromText(
        newTryStatement.toString(), element);
    if (replaceAll) {
      for (PsiStatement newStatement : newCodeBlock.getStatements()) {
        tryStatement.getParent().addBefore(newStatement,
                                           tryStatement);
      }
      tryStatement.delete();
    }
    else {
      resourceList.delete();
      tryBlock.replace(newCodeBlock);
    }
  }
}
