/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
public class SuggestFirstVariableNameMacro extends VariableOfTypeMacro {
  @Override
  public String getName() {
    return "suggestFirstVariableName";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightLocalize.macroSuggestFirstVariableName().get();
  }

  @Override
  @RequiredReadAction
  protected PsiElement[] getVariables(Expression[] params, ExpressionContext context) {
    PsiElement[] variables = super.getVariables(params, context);
    if (variables == null) return null;
    List<PsiElement> result = new ArrayList<>();
    List<String> skip = Arrays.asList("true", "false", "this", "super");
    for (PsiElement variable : variables) {
      if (!skip.contains(variable.getText())) {
        result.add(variable);
      }
    }
    return PsiUtilCore.toPsiElementArray(result);
  }
}


