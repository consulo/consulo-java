/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.impl.codeInsight.template.JavaCodeContextType;
import com.intellij.java.language.impl.codeInsight.template.macro.MacroUtil;
import com.intellij.java.language.impl.codeInsight.template.macro.PsiTypeResult;
import com.intellij.java.language.psi.PsiArrayType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.PsiElementResult;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class ComponentTypeOfMacro extends Macro {
  @Override
  public String getName() {
    return "componentTypeOf";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightLocalize.macroComponentTypeOfArray().get();
  }

  @Override
  public LookupElement[] calculateLookupItems(@Nonnull Expression[] params, ExpressionContext context) {
    if (params.length != 1) {
      return null;
    }
    LookupElement[] lookupItems = params[0].calculateLookupItems(context);
    if (lookupItems == null) {
      return null;
    }

    List<LookupElement> result = new ArrayList<>();
    for (LookupElement element : lookupItems) {
      PsiTypeLookupItem lookupItem = element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY);
      if (lookupItem != null) {
        PsiType psiType = lookupItem.getType();
        if (psiType instanceof PsiArrayType arrayType) {
          result.add(PsiTypeLookupItem.createLookupItem(arrayType.getComponentType(), null));
        }
      }
    }

    return lookupItems;
  }

  @Override
  @RequiredReadAction
  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    if (params.length != 1) {
      return null;
    }
    Result result = params[0].calculateResult(context);
    if (result == null) {
      return null;
    }

    if (result instanceof PsiTypeResult typeResult) {
      PsiType type = typeResult.getType();
      if (type instanceof PsiArrayType arrayType) {
        return new PsiTypeResult(arrayType.getComponentType(), context.getProject());
      }
    }

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    PsiType type = expr == null ? MacroUtil.resultToPsiType(result, context) : expr.getType();
    if (type instanceof PsiArrayType arrayType) {
      return new PsiTypeResult(arrayType.getComponentType(), context.getProject());
    }

    LookupElement[] elements = params[0].calculateLookupItems(context);
    if (elements != null) {
      for (LookupElement element : elements) {
        PsiTypeLookupItem typeLookupItem = element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY);
        if (typeLookupItem != null) {
          PsiType psiType = typeLookupItem.getType();
          if (psiType instanceof PsiArrayType arrayType) {
            return new PsiTypeResult(arrayType.getComponentType(), context.getProject());
          }
        }
      }
    }

    return new PsiElementResult(null);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}
