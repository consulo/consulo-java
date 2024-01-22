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
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public abstract class VariableTypeMacroBase extends Macro {
  @Nullable
  protected abstract PsiElement[] getVariables(Expression[] params, final ExpressionContext context);

  @Override
  public LookupElement[] calculateLookupItems(@jakarta.annotation.Nonnull Expression[] params, final ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length < 2) return null;
    Set<LookupElement> set = new LinkedHashSet<LookupElement>();
    for (PsiElement element : vars) {
      JavaEditorTemplateUtilImpl.addElementLookupItem(set, element);
    }
    return set.toArray(new LookupElement[set.size()]);
  }

  @Override
  public Result calculateResult(@jakarta.annotation.Nonnull Expression[] params, ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length == 0) return null;
    return new JavaPsiElementResult(vars[0]);
  }

  @Override
  @Nonnull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
