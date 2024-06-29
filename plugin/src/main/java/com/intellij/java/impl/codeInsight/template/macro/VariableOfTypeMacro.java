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
import com.intellij.java.impl.codeInsight.template.JavaEditorTemplateUtilImpl;
import com.intellij.java.impl.codeInsight.template.JavaPsiElementResult;
import com.intellij.java.language.impl.codeInsight.template.macro.MacroUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@ExtensionImpl
public class VariableOfTypeMacro extends Macro {

  @Override
  public String getName() {
    return "variableOfType";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightLocalize.macroVariableOfType().get();
  }

  @Override
  @Nonnull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length == 0) return null;
    return new JavaPsiElementResult(vars[0]);
  }

  @Override
  public LookupElement[] calculateLookupItems(@Nonnull Expression[] params, final ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length < 2) return null;
    final Set<LookupElement> set = new LinkedHashSet<>();
    for (PsiElement var : vars) {
      JavaEditorTemplateUtilImpl.addElementLookupItem(set, var);
    }
    return set.toArray(new LookupElement[set.size()]);
  }

  @Nullable
  protected PsiElement[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    Project project = context.getProject();
    final int offset = context.getStartOffset();

    final ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    PsiType type = MacroUtil.resultToPsiType(result, context);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);

    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    PsiManager manager = PsiManager.getInstance(project);
    for (PsiVariable var : variables) {
      if (var instanceof PsiField field && var.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass varClass = field.getContainingClass();
        PsiClass placeClass = PsiTreeUtil.getParentOfType(place, PsiClass.class);
        if (!manager.areElementsEquivalent(varClass, placeClass)) continue;
      }
      else if (var instanceof PsiLocalVariable) {
        final TextRange range = var.getNameIdentifier().getTextRange();
        if (range != null && range.contains(offset)) {
          continue;
        }
      }

      PsiType type1 = VariableTypeCalculator.getVarTypeAt(var, place);
      if (type == null || type.isAssignableFrom(type1)) {
        array.add(var);
      }
    }

    PsiExpression[] expressions = MacroUtil.getStandardExpressionsOfType(place, type);
    ContainerUtil.addAll(array, expressions);
    return PsiUtilCore.toPsiElementArray(array);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}

