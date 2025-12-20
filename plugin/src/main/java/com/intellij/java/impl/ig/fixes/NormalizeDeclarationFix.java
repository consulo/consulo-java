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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class NormalizeDeclarationFix extends InspectionGadgetsFix {

  @Nonnull
  public LocalizeValue getName() {
    return InspectionGadgetsLocalize.normalizeDeclarationQuickfix();
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    PsiElement variableNameElement = descriptor.getPsiElement();
    PsiVariable parent = (PsiVariable)variableNameElement.getParent();
    if (parent == null) {
      return;
    }
    if (parent instanceof PsiField) {
      parent.normalizeDeclaration();
      return;
    }
    PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiDeclarationStatement)) {
      return;
    }
    PsiElement greatGrandParent = grandParent.getParent();
    if (greatGrandParent instanceof PsiForStatement) {
      PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)grandParent;
      splitMultipleDeclarationInForStatementInitialization(declarationStatement);
      return;
    }
    parent.normalizeDeclaration();
  }

  private static void splitMultipleDeclarationInForStatementInitialization(
    PsiDeclarationStatement declarationStatement) {
    PsiElement forStatement = declarationStatement.getParent();
    PsiElement[] declaredElements =
      declarationStatement.getDeclaredElements();
    Project project = forStatement.getProject();
    PsiElementFactory factory =
      JavaPsiFacade.getElementFactory(project);
    PsiElement greatGreatGrandParent = forStatement.getParent();
    PsiBlockStatement blockStatement;
    PsiCodeBlock codeBlock;
    if (!(greatGreatGrandParent instanceof PsiCodeBlock)) {
      blockStatement = (PsiBlockStatement)
        factory.createStatementFromText("{}", forStatement);
      codeBlock = blockStatement.getCodeBlock();
    }
    else {
      blockStatement = null;
      codeBlock = null;
    }
    for (int i = 1; i < declaredElements.length; i++) {
      PsiElement declaredElement = declaredElements[i];
      if (!(declaredElement instanceof PsiVariable)) {
        continue;
      }
      PsiVariable variable = (PsiVariable)declaredElement;
      PsiType type = variable.getType();
      String typeText = type.getCanonicalText();
      StringBuilder newStatementText =
        new StringBuilder(typeText);
      newStatementText.append(' ');
      newStatementText.append(variable.getName());
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        newStatementText.append('=');
        newStatementText.append(initializer.getText());
      }
      newStatementText.append(';');
      PsiStatement newStatement =
        factory.createStatementFromText(
          newStatementText.toString(), forStatement);
      if (codeBlock == null) {
        greatGreatGrandParent.addBefore(newStatement, forStatement);
      }
      else {
        codeBlock.add(newStatement);
      }
    }
    for (int i = 1; i < declaredElements.length; i++) {
      PsiElement declaredElement = declaredElements[i];
      if (!(declaredElement instanceof PsiVariable)) {
        continue;
      }
      declaredElement.delete();
    }
    if (codeBlock != null) {
      codeBlock.add(forStatement);
      forStatement.replace(blockStatement);
    }
  }
}
