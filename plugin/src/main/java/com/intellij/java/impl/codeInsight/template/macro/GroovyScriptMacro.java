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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.TextResult;
import consulo.language.editor.template.macro.Macro;

import jakarta.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
@ExtensionImpl
public class GroovyScriptMacro extends Macro {
  @Override
  public String getName() {
    return "groovyScript";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.groovy.script");
  }

  @Override
  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return null;
    Object o = runIt(params, context);
    if (o != null) return new TextResult(o.toString());
    return null;
  }

  private static Object runIt(Expression[] params, ExpressionContext context) {
   /*
   TODO [VISTALL] groovy depend
      try {
      Result result = params[0].calculateResult(context);
      if (result == null) return result;
      String text = result.toString();
      GroovyShell shell = new GroovyShell();
      File possibleFile = new File(text);
      Script script = possibleFile.exists() ? shell.parse(possibleFile) :  shell.parse(text);
      Binding binding = new Binding();

      for(int i = 1; i < params.length; ++i) {
        Result paramResult = params[i].calculateResult(context);
        Object value = null;
        if (paramResult instanceof ListResult) {
          value = ContainerUtil.map2List(((ListResult)paramResult).getComponents(), new Function<Result, String>() {
            @Override
            public String apply(Result result) {
              return result.toString();
            }
          });
        } else if (paramResult != null) {
          value = paramResult.toString();
        }
        binding.setVariable("_"+i, value);
      }

      binding.setVariable("_editor", context.getEditor());

      script.setBinding(binding);

      Object o = script.run();
      return o != null ? StringUtil.convertLineSeparators(o.toString()):null;
    } catch (Exception e) {
      return new TextResult(StringUtil.convertLineSeparators(e.getLocalizedMessage()));
    }    */
    return null;
  }

  @Override
  public Result calculateQuickResult(@Nonnull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public LookupElement[] calculateLookupItems(@Nonnull Expression[] params, ExpressionContext context) {
    Object o = runIt(params, context);
    if (o != null) {
      Set<LookupElement> set = new LinkedHashSet<LookupElement>();
      set.add(LookupElementBuilder.create(o.toString()));
      return set.toArray(new LookupElement[set.size()]);
    }
    return LookupElement.EMPTY_ARRAY;
  }
}
