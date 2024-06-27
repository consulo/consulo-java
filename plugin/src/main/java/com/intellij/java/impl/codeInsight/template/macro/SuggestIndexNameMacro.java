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
import com.intellij.java.language.impl.codeInsight.template.macro.MacroUtil;
import com.intellij.java.language.psi.PsiDeclarationStatement;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiVariable;
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
public class SuggestIndexNameMacro extends Macro {
  @Override
  public String getName() {
    return "suggestIndexName";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightLocalize.macroSuggestIndexName().get();
  }

  @Override
  @Nonnull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  @RequiredReadAction
  public Result calculateResult(@Nonnull Expression[] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    final Project project = context.getProject();
    final int offset = context.getStartOffset();

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    PsiVariable[] vars = MacroUtil.getVariablesVisibleAt(place, "");
  ChooseLetterLoop:
    for (char letter = 'i'; letter <= 'z'; letter++){
      for (PsiVariable var : vars) {
        PsiIdentifier identifier = var.getNameIdentifier();
        if (identifier == null || place.equals(identifier)) continue;
        if (var instanceof PsiLocalVariable) {
          PsiElement parent = var.getParent();
          if (parent instanceof PsiDeclarationStatement) {
            if (PsiTreeUtil.isAncestor(parent, place, false) &&
                var.getTextRange().getStartOffset() > place.getTextRange().getStartOffset()) {
              continue;
            }
          }
        }
        String name = identifier.getText();
        if (name.length() == 1 && name.charAt(0) == letter) {
          continue ChooseLetterLoop;
        }
      }
      return new TextResult("" + letter);
    }

    return null;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}