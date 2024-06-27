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
package com.intellij.java.impl.codeInsight.template.macro;

import com.intellij.java.impl.codeInsight.template.JavaCodeContextType;
import com.intellij.java.language.impl.codeInsight.template.macro.PsiTypeResult;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;

import jakarta.annotation.Nonnull;


/**
 * @author ven
 */
@ExtensionImpl
public class RightSideTypeMacro extends Macro {
  @Override
  public String getName() {
    return "rightSideType";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightLocalize.macroRightSideType().get();
  }

  @Override
  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    int offset = context.getStartOffset();
    Project project = context.getProject();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement element = file.findElementAt(offset);
    element = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, PsiVariable.class);
    if (element instanceof PsiAssignmentExpression assignment) {
      PsiExpression rhs = assignment.getRExpression();
      if (rhs == null) return null;
      final PsiType rhsType = rhs.getType();
      if (rhsType == null) return null;
      return new PsiTypeResult(rhsType, project);
    } else if (element instanceof PsiVariable var) {
      PsiExpression initializer = var.getInitializer();
      if (initializer == null) return null;
      PsiType type = RefactoringUtil.getTypeByExpression(initializer);
      if (type == null) return null;
      return new PsiTypeResult(type, project);
    }
    return null;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}
