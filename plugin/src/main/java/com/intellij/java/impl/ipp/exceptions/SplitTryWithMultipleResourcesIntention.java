/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.SplitTryWithMultipleResourcesIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class SplitTryWithMultipleResourcesIntention extends Intention {

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new TryWithMultipleResourcesPredicate();
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
    final PsiTryStatement tryStatement = (PsiTryStatement)element.getParent();
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList == null) {
      return;
    }
    @NonNls final StringBuilder newTryStatementText = new StringBuilder();
    final List<PsiResourceVariable> variables = resourceList.getResourceVariables();
    boolean braces = false;
    for (PsiResourceVariable variable : variables) {
      if (braces) {
        newTryStatementText.append("{\n");
      } else {
        braces = true;
      }
      newTryStatementText.append("try (").append(variable.getText()).append(")");
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return;
    }
    newTryStatementText.append(tryBlock.getText());
    for (int i = 1; i < variables.size(); i++) {
      newTryStatementText.append("\n}");
    }
    final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    for (PsiCatchSection catchSection : catchSections) {
      newTryStatementText.append(catchSection.getText());
    }
    replaceStatement(newTryStatementText.toString(), tryStatement);
  }
}
