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
import com.intellij.java.language.impl.codeInsight.template.macro.MacroUtil;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.*;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;

import javax.annotation.Nonnull;

@ExtensionImpl
public class TypeOfVariableMacro extends Macro {
  @Override
  public String getName() {
    return "typeOfVariable";
  }

  @Override
  public String getPresentableName() {
    return "typeOfVariable(VAR)";
  }

  @Override
  @Nonnull
  public String getDefaultValue() {
    return "A";
  }

  @Override
  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return null;

    final Project project = context.getProject();
    Result result = params[0].calculateQuickResult(context);
    if (result instanceof PsiElementResult) {
      final PsiElement element = ((PsiElementResult)result).getElement();
      if (element instanceof PsiVariable) {
        return new PsiTypeResult(((PsiVariable)element).getType(), project);
      }
    } else if (result instanceof TextResult) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
      PsiElement place = file.findElementAt(context.getStartOffset());
      final PsiVariable[] vars = MacroUtil.getVariablesVisibleAt(place, "");
      final String name = result.toString();
      for (final PsiVariable var : vars) {
        if (name.equals(var.getName())) return new PsiTypeResult(var.getType(), project);
      }
    }
    return null;
  }

  @Override
  public Result calculateQuickResult(@Nonnull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}