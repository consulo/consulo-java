/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import javax.annotation.Nonnull;

import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.java.impl.refactoring.introduceVariable.InputValidator;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;

// todo: support for int[].var (parses as .class access!)
public class IntroduceVariablePostfixTemplate extends ExpressionPostfixTemplateWithChooser {
  public IntroduceVariablePostfixTemplate() {
    super("var", "T name = expr;");
  }

  @Override
  protected void doIt(@Nonnull Editor editor, @Nonnull PsiExpression expression) {
    // for advanced stuff use ((PsiJavaCodeReferenceElement)expression).advancedResolve(true).getElement();
    IntroduceVariableHandler handler = ApplicationManager.getApplication().isUnitTestMode() ? getMockHandler() : new IntroduceVariableHandler();
    handler.invoke(expression.getProject(), editor, expression);
  }

  @Nonnull
  private static IntroduceVariableHandler getMockHandler() {
    return new IntroduceVariableHandler() {
      // mock default settings
      @Override
      public final IntroduceVariableSettings getSettings(Project project, Editor editor, final PsiExpression expr,
                                                         PsiExpression[] occurrences, TypeSelectorManagerImpl typeSelectorManager,
                                                         boolean declareFinalIfAll, boolean anyAssignmentLHS, InputValidator validator,
                                                         PsiElement anchor, OccurrencesChooser.ReplaceChoice replaceChoice) {
        return new IntroduceVariableSettings() {
          @Override
          public String getEnteredName() {
            return "foo";
          }

          @Override
          public boolean isReplaceAllOccurrences() {
            return false;
          }

          @Override
          public boolean isDeclareFinal() {
            return false;
          }

          @Override
          public boolean isReplaceLValues() {
            return false;
          }

          @Override
          public PsiType getSelectedType() {
            return expr.getType();
          }

          @Override
          public boolean isOK() {
            return true;
          }
        };
      }
    };
  }
}