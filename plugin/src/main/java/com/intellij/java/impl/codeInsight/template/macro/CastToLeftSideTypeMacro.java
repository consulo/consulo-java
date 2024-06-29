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

/*
 * @author ven
 */
package com.intellij.java.impl.codeInsight.template.macro;

import com.intellij.java.impl.codeInsight.template.JavaCodeContextType;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.TextResult;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CastToLeftSideTypeMacro extends Macro {
  @Override
  public String getName() {
    return "castToLeftSideType";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightLocalize.macroCastToLeftSideType().get();
  }

  @Override
  @Nonnull
  public String getDefaultValue() {
    return "(A)";
  }

  @Override
  @RequiredReadAction
  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    int offset = context.getStartOffset();
    Project project = context.getProject();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement element = file.findElementAt(offset);
    element = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, PsiVariable.class);
    PsiType leftType = null;
    PsiExpression rightSide = null;
    if (element instanceof PsiAssignmentExpression assignment) {
      leftType  = assignment.getLExpression().getType();
      rightSide = assignment.getRExpression();
    } else if (element instanceof PsiVariable var) {
      leftType = var.getType();
      rightSide = var.getInitializer();
    }

    while (rightSide instanceof PsiTypeCastExpression typeCastExpression) {
      rightSide = typeCastExpression.getOperand();
    }

    if (leftType != null && rightSide != null && rightSide.getType() != null && !leftType.isAssignableFrom(rightSide.getType())) {
        return new TextResult("("+ leftType.getCanonicalText() + ")");
    }

    return new TextResult("");
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}