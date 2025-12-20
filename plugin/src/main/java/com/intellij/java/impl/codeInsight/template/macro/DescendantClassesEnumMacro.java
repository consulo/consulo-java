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
import com.intellij.java.impl.codeInsight.template.JavaPsiElementResult;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.PsiManager;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.language.psi.scope.GlobalSearchScope;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ExtensionImpl
public class DescendantClassesEnumMacro extends Macro {
  @Override
  public String getName() {
    return "descendantClassesEnum";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightLocalize.macroDescendantClassesEnum().get();
  }

  @Override
  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.size() == 0) return null;
    Result[] results = calculateResults(classes);

    return results[0];
  }

  private static Result[] calculateResults(List<PsiClass> classes) {
    Result[] results = new Result[classes.size()];
    int i = 0;

    for (PsiClass aClass : classes) {
      results[i++] = new JavaPsiElementResult(aClass);
    }
    return results;
  }

  @Nullable
  private static List<PsiClass> findDescendants(ExpressionContext context, Expression[] params) {
    if (params == null || params.length == 0) return null;
    PsiManager instance = PsiManager.getInstance(context.getProject());

    Result result = params[0].calculateResult(context);
    if (result == null) return null;
    
    String paramResult = result.toString();
    if (paramResult == null) return null;

    boolean isAllowAbstract = isAllowAbstract(context, params);
    PsiClass myBaseClass =
      JavaPsiFacade.getInstance(instance.getProject()).findClass(paramResult, GlobalSearchScope.allScope(context.getProject()));

    if (myBaseClass != null) {
      List<PsiClass> classes = new ArrayList<>();

      ClassInheritorsSearch.search(myBaseClass, true)
        .forEach(new PsiElementProcessorAdapter<>((PsiElementProcessor<PsiClass>)element -> {
          if (isAllowAbstract || !isAbstractOrInterface(element)) {
            classes.add(element);
          }
          return true;
        }));

      return classes;
    }

    return null;
  }

  @Override
  public Result calculateQuickResult(@Nonnull Expression[] params, ExpressionContext context) {
    List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.size() == 0) return null;
    Result[] results = calculateResults(classes);

    return results[0];
  }

  @Override
  public LookupElement[] calculateLookupItems(@Nonnull Expression[] params, ExpressionContext context) {
    List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.size() == 0) return null;

    Set<LookupElement> set = new LinkedHashSet<>();
    boolean isShortName = params.length > 1 && !Boolean.valueOf(params[1].calculateResult(context).toString());

    for (PsiClass object : classes) {
      String name = isShortName ? object.getName() : object.getQualifiedName();
      if (name != null && name.length() > 0) {
        set.add(LookupElementBuilder.create(name));
      }
    }

    return set.toArray(new LookupElement[set.size()]);
  }

  private static boolean isAbstractOrInterface(PsiClass psiClass) {
    PsiModifierList modifierList = psiClass.getModifierList();

    return psiClass.isInterface() || (modifierList != null && modifierList.hasModifierProperty(PsiModifier.ABSTRACT));
  }

  private static boolean isAllowAbstract(ExpressionContext context, Expression[] params) {
      return params.length > 2 ? Boolean.valueOf(params[2].calculateResult(context).toString()) : true;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}