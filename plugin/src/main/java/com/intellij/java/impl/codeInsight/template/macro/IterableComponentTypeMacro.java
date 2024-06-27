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
import com.intellij.java.language.impl.codeInsight.template.macro.PsiTypeResult;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
@ExtensionImpl
public class IterableComponentTypeMacro extends Macro {
  @Override
  public String getName() {
    return "iterableComponentType";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightLocalize.macroIterableComponentType().get();
  }

  @Override
  @Nonnull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  @RequiredReadAction
  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    Project project = context.getProject();

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    if (expr == null) return null;
    PsiType type = expr.getType();


    if (type instanceof PsiArrayType arrayType) {
      return new PsiTypeResult(arrayType.getComponentType(), project);
    }

    if (type instanceof PsiClassType classType) {
      PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = resolveResult.getElement();

      if (aClass != null) {
        PsiClass iterableClass = JavaPsiFacade.getInstance(project).findClass("java.lang.Iterable", aClass.getResolveScope());
        if (iterableClass != null) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(iterableClass, aClass, resolveResult.getSubstitutor());
          if (substitutor != null) {
            PsiType parameterType = substitutor.substitute(iterableClass.getTypeParameters()[0]);
            if (parameterType instanceof PsiCapturedWildcardType capturedWildcardType) {
              parameterType = capturedWildcardType.getWildcard();
            }
            if (parameterType != null) {
              if (parameterType instanceof PsiWildcardType wildcardType) {
                if (wildcardType.isExtends()) {
                  return new PsiTypeResult(wildcardType.getBound(), project);
                }
                else return null;
              }
              return new PsiTypeResult(parameterType, project);
            }
          }
        }
      }
    }

    return null;
  }

  @Override
  @RequiredReadAction
  public Result calculateQuickResult(@Nonnull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}
