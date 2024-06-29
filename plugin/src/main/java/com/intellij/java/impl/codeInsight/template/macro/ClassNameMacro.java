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
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiTypeParameter;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.TextResult;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ClassNameMacro extends Macro {

  @Override
  public String getName() {
    return "className";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightLocalize.macroClassname().get();
  }

  @Override
  @RequiredReadAction
  public Result calculateResult(@Nonnull Expression[] params, final ExpressionContext context) {
    int templateStartOffset = context.getTemplateStartOffset();
    int offset = templateStartOffset > 0 ? context.getTemplateStartOffset() - 1 : context.getTemplateStartOffset();
    PsiElement place = context.getPsiElementAtStartOffset();
    PsiClass aClass = null;

    while (place != null) {
      if (place instanceof PsiClass placeClass && !(place instanceof PsiAnonymousClass) && !(place instanceof PsiTypeParameter)) {
        aClass = placeClass;
        // if className() is evaluated outside of the body of inner class, return name of its outer class instead (IDEADEV-19865)
        final PsiElement lBrace = aClass.getLBrace();
        if (lBrace != null && offset < lBrace.getTextOffset() && aClass.getContainingClass() != null) {
          aClass = aClass.getContainingClass();
        }
        break;
      }
      if (place instanceof PsiJavaFile javaFile) {
        PsiClass[] classes = javaFile.getClasses();
        aClass = classes.length != 0 ? classes[0] : null;
        break;
      }
      place = place.getParent();
    }

    if (aClass == null) return null;
    String result = aClass.getName();
    while (aClass.getContainingClass() != null && aClass.getContainingClass().getName() != null) {
      result = aClass.getContainingClass().getName() + "$" + result;
      aClass = aClass.getContainingClass();
    }
    return new TextResult(result);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}
