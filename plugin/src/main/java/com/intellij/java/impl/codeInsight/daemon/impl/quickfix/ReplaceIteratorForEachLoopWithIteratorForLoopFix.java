/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

/**
 * @author Pavel.Dolgov
 */
public class ReplaceIteratorForEachLoopWithIteratorForLoopFix implements SyntheticIntentionAction {
  private final PsiForeachStatement myStatement;

  public ReplaceIteratorForEachLoopWithIteratorForLoopFix(@Nonnull PsiForeachStatement statement) {
    myStatement = statement;
  }

  @Nonnull
  @Override
  public LocalizeValue getText() {
    return LocalizeValue.localizeTODO("Replace 'for each' loop with iterator 'for' loop");
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myStatement.isValid() && myStatement.getManager().isInProject(myStatement);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiExpression iteratedValue = myStatement.getIteratedValue();
    if (iteratedValue == null) {
      return;
    }
    PsiType iteratedValueType = iteratedValue.getType();
    if (iteratedValueType == null) {
      return;
    }
    PsiParameter iterationParameter = myStatement.getIterationParameter();
    String iterationParameterName = iterationParameter.getName();
    if (iterationParameterName == null) {
      return;
    }
    PsiStatement forEachBody = myStatement.getBody();

    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    JavaCodeStyleManager javaStyleManager = JavaCodeStyleManager.getInstance(project);
    String name = javaStyleManager.suggestUniqueVariableName("it", myStatement, true);
    PsiForStatement newForLoop =
      (PsiForStatement)elementFactory.createStatementFromText("for (Iterator " + name + " = initializer; " + name + ".hasNext();) { Object next = " + name + ".next();" +
                                                                " }", myStatement);

    PsiDeclarationStatement newDeclaration = (PsiDeclarationStatement)newForLoop.getInitialization();
    if (newDeclaration == null) {
      return;
    }
    PsiLocalVariable newIteratorVariable = (PsiLocalVariable)newDeclaration.getDeclaredElements()[0];
    PsiTypeElement newIteratorTypeElement = elementFactory.createTypeElement(iteratedValueType);
    newIteratorVariable.getTypeElement().replace(newIteratorTypeElement);
    newIteratorVariable.setInitializer(iteratedValue);

    PsiBlockStatement newBody = (PsiBlockStatement)newForLoop.getBody();
    if (newBody == null) {
      return;
    }
    PsiCodeBlock newBodyBlock = newBody.getCodeBlock();

    PsiDeclarationStatement newFirstStatement = (PsiDeclarationStatement)newBodyBlock.getStatements()[0];
    PsiLocalVariable newItemVariable = (PsiLocalVariable)newFirstStatement.getDeclaredElements()[0];
    PsiTypeElement newItemTypeElement = elementFactory.createTypeElement(iterationParameter.getType());
    newItemVariable.getTypeElement().replace(newItemTypeElement);
    newItemVariable.setName(iterationParameterName);
    CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
    if (codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS) {
      PsiModifierList modifierList = newItemVariable.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
      }
    }
    CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    newForLoop = (PsiForStatement)javaStyleManager.shortenClassReferences(newForLoop);
    newForLoop = (PsiForStatement)styleManager.reformat(newForLoop);

    if (forEachBody instanceof PsiBlockStatement) {
      PsiCodeBlock bodyCodeBlock = ((PsiBlockStatement)forEachBody).getCodeBlock();
      PsiElement firstBodyElement = bodyCodeBlock.getFirstBodyElement();
      PsiElement lastBodyElement = bodyCodeBlock.getLastBodyElement();
      if (firstBodyElement != null && lastBodyElement != null) {
        newBodyBlock.addRangeAfter(firstBodyElement, lastBodyElement, newFirstStatement);
      }
    }
    else if (forEachBody != null && !(forEachBody instanceof PsiEmptyStatement)) {
      newBodyBlock.addAfter(forEachBody, newFirstStatement);
    }

    myStatement.replace(newForLoop);
  }
}
