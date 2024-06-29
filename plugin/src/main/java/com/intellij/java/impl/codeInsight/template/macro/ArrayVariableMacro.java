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

import com.intellij.java.language.impl.codeInsight.template.macro.MacroUtil;
import com.intellij.java.language.psi.PsiArrayType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;

import java.util.ArrayList;

@ExtensionImpl
public class ArrayVariableMacro extends VariableTypeMacroBase {
  @Override
  public String getName() {
    return "arrayVariable";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightLocalize.macroArrayVariable().get();
  }

  @Override
  @RequiredReadAction
  protected PsiElement[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    Project project = context.getProject();
    final int offset = context.getStartOffset();
    final ArrayList<PsiVariable> array = new ArrayList<>();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    for (PsiVariable variable : variables) {
      PsiType type = VariableTypeCalculator.getVarTypeAt(variable, place);
      if (type instanceof PsiArrayType) {
        array.add(variable);
      }
    }
    return array.toArray(new PsiVariable[array.size()]);
  }
}
