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
import com.intellij.java.indexing.search.searches.AnnotatedMembersSearch;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.TextResult;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
@ExtensionImpl
public class  AnnotatedMacro extends Macro {

  @Override
  @NonNls
  public String getName() {
    return "annotated";
  }

  @Override
  public String getPresentableName() {
    return "annotated(\"annotation qname\")";
  }

  @Nullable
  private static Query<PsiMember> findAnnotated(ExpressionContext context, Expression[] params) {
    if (params == null || params.length == 0) return null;
    PsiManager instance = PsiManager.getInstance(context.getProject());

    final String paramResult = params[0].calculateResult(context).toString();
    if (paramResult == null) return null;
    final GlobalSearchScope scope = GlobalSearchScope.allScope(context.getProject());
    final PsiClass myBaseClass = JavaPsiFacade.getInstance(instance.getProject()).findClass(paramResult,  scope);

    if (myBaseClass != null) {
      return AnnotatedMembersSearch.search(myBaseClass, scope);
    }
    return null;
  }

  @Override
  public Result calculateResult(@Nonnull Expression[] expressions, ExpressionContext expressionContext) {
    final Query<PsiMember> psiMembers = findAnnotated(expressionContext, expressions);

    if (psiMembers != null) {
      final PsiMember member = psiMembers.findFirst();

      if (member != null) {
        return new TextResult(member instanceof PsiClass ? ((PsiClass)member).getQualifiedName():member.getName());
      }
    }
    return null;
  }

  @Override
  public Result calculateQuickResult(@Nonnull Expression[] expressions, ExpressionContext expressionContext) {
    return calculateResult(expressions, expressionContext);
  }

  @Override
  public LookupElement[] calculateLookupItems(@Nonnull Expression[] params, ExpressionContext context) {
    final Query<PsiMember> query = findAnnotated(context, params);

    if (query != null) {
      Set<LookupElement> set = new LinkedHashSet<LookupElement>();
      final String secondParamValue = params.length > 1 ? params[1].calculateResult(context).toString() : null;
      final boolean isShortName = secondParamValue != null && !Boolean.valueOf(secondParamValue);
      final Project project = context.getProject();
      final PsiClass findInClass = secondParamValue != null
                                   ? JavaPsiFacade.getInstance(project).findClass(secondParamValue, GlobalSearchScope.allScope(project))
                                   : null;

      for (PsiMember object : query.findAll()) {
        if (findInClass != null && !object.getContainingClass().equals(findInClass)) continue;
        boolean isClazz = object instanceof PsiClass;
        final String name = isShortName || !isClazz ? object.getName() : ((PsiClass) object).getQualifiedName();
        set.add(LookupElementBuilder.create(name));
      }

      return set.toArray(new LookupElement[set.size()]);
    }
    return LookupElement.EMPTY_ARRAY;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
