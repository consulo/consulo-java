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

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.codeInsight.template.JavaCodeContextType;
import com.intellij.java.impl.codeInsight.template.JavaEditorTemplateUtilImpl;
import com.intellij.java.language.impl.codeInsight.template.macro.PsiTypeResult;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

import jakarta.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

@ExtensionImpl
public class SubtypesMacro extends Macro {
  @Override
  public String getName() {
    return "subtypes";
  }

  @Override
  public String getPresentableName() {
    return "subtypes(TYPE)";
  }

  @Override
  @Nonnull
  public String getDefaultValue() {
    return "A";
  }

  @Override
  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return null;
    return params[0].calculateQuickResult(context);
  }

  @Override
  public Result calculateQuickResult(@Nonnull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public LookupElement[] calculateLookupItems(@Nonnull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return LookupElement.EMPTY_ARRAY;
    Result paramResult = params[0].calculateQuickResult(context);
    if (paramResult instanceof PsiTypeResult) {
      PsiType type = ((PsiTypeResult)paramResult).getType();
      PsiFile file = PsiDocumentManager.getInstance(context.getProject()).getPsiFile(context.getEditor().getDocument());
      PsiElement element = file.findElementAt(context.getStartOffset());

      final Set<LookupElement> set = new LinkedHashSet<LookupElement>();
      JavaEditorTemplateUtilImpl.addTypeLookupItem(set, type);
      CodeInsightUtil.processSubTypes(type, element, false, PrefixMatcher.ALWAYS_TRUE, new Consumer<PsiType>() {
        @Override
        public void accept(PsiType psiType) {
          JavaEditorTemplateUtilImpl.addTypeLookupItem(set, psiType);
        }
      });
      return set.toArray(new LookupElement[set.size()]);
    }
    return LookupElement.EMPTY_ARRAY;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}