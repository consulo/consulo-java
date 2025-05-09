/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.filters.getters;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.codeInsight.completion.StaticMemberProcessor;
import com.intellij.java.indexing.impl.stubs.index.JavaStaticMemberTypeIndex;
import com.intellij.java.language.impl.psi.scope.processor.FilterScopeProcessor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.editor.completion.AutoCompletionPolicy;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.filter.TrueFilter;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * @author ik
 * @author peter
 */
public abstract class MembersGetter {
  public static final Key<Boolean> EXPECTED_TYPE_MEMBER = Key.create("EXPECTED_TYPE_MEMBER");
  private final Set<PsiMember> myImportedStatically = new HashSet<>();
  private final List<PsiClass> myPlaceClasses = new ArrayList<>();
  private final List<PsiMethod> myPlaceMethods = new ArrayList<>();
  protected final PsiElement myPlace;

  protected MembersGetter(StaticMemberProcessor processor, @Nonnull final PsiElement place) {
    myPlace = place;
    processor.processMembersOfRegisteredClasses(PrefixMatcher.ALWAYS_TRUE, (member, psiClass) -> myImportedStatically.add(member));

    PsiClass current = PsiTreeUtil.getContextOfType(place, PsiClass.class);
    while (current != null) {
      current = CompletionUtilCore.getOriginalOrSelf(current);
      myPlaceClasses.add(current);
      current = PsiTreeUtil.getContextOfType(current, PsiClass.class);
    }

    PsiMethod eachMethod = PsiTreeUtil.getContextOfType(place, PsiMethod.class);
    while (eachMethod != null) {
      eachMethod = CompletionUtilCore.getOriginalOrSelf(eachMethod);
      myPlaceMethods.add(eachMethod);
      eachMethod = PsiTreeUtil.getContextOfType(eachMethod, PsiMethod.class);
    }

  }

  private boolean mayProcessMembers(@Nullable PsiClass psiClass) {
    if (psiClass == null) {
      return false;
    }

    for (PsiClass placeClass : myPlaceClasses) {
      if (InheritanceUtil.isInheritorOrSelf(placeClass, psiClass, true)) {
        return false;
      }
    }
    return true;
  }

  public void processMembers(final Consumer<LookupElement> results, @Nullable final PsiClass where, final boolean acceptMethods, final boolean searchInheritors) {
    if (where == null || isPrimitiveClass(where)) {
      return;
    }

    final boolean searchFactoryMethods = searchInheritors && !CommonClassNames.JAVA_LANG_OBJECT.equals(where.getQualifiedName()) && !isPrimitiveClass(where);

    final Project project = myPlace.getProject();
    final GlobalSearchScope scope = myPlace.getResolveScope();

    final PsiClassType baseType = JavaPsiFacade.getElementFactory(project).createType(where);
    Consumer<PsiType> consumer = psiType ->
    {
      PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
      if (psiClass == null) {
        return;
      }
      psiClass = CompletionUtilCore.getOriginalOrSelf(psiClass);
      if (mayProcessMembers(psiClass)) {
        final FilterScopeProcessor<PsiElement> declProcessor = new FilterScopeProcessor<>(TrueFilter.INSTANCE);
        psiClass.processDeclarations(declProcessor, ResolveState.initial(), null, myPlace);
        doProcessMembers(acceptMethods, results, psiType == baseType, declProcessor.getResults());

        String name = psiClass.getName();
        if (name != null && searchFactoryMethods) {
          Collection<PsiMember> factoryMethods = JavaStaticMemberTypeIndex.getInstance().getStaticMembers(name, project, scope);
          doProcessMembers(acceptMethods, results, false, factoryMethods);
        }
      }
    };
    consumer.accept(baseType);
    if (searchInheritors && !CommonClassNames.JAVA_LANG_OBJECT.equals(where.getQualifiedName())) {
      CodeInsightUtil.processSubTypes(baseType, myPlace, true, PrefixMatcher.ALWAYS_TRUE, consumer);
    }
  }

  private static boolean isPrimitiveClass(PsiClass where) {
    String qname = where.getQualifiedName();
    if (qname == null || !qname.startsWith("java.lang.")) {
      return false;
    }
    return CommonClassNames.JAVA_LANG_STRING.equals(qname) || InheritanceUtil.isInheritor(where, CommonClassNames.JAVA_LANG_NUMBER);
  }

  private void doProcessMembers(boolean acceptMethods, Consumer<LookupElement> results, boolean isExpectedTypeMember, Collection<? extends PsiElement> declarations) {
    for (final PsiElement result : declarations) {
      if (result instanceof PsiMember && !(result instanceof PsiClass)) {
        final PsiMember member = (PsiMember) result;
        if (!member.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        if (result instanceof PsiField && !member.hasModifierProperty(PsiModifier.FINAL)) {
          continue;
        }
        if (result instanceof PsiMethod && (!acceptMethods || myPlaceMethods.contains(result))) {
          continue;
        }
        if (JavaCompletionUtil.isInExcludedPackage(member, false) || myImportedStatically.contains(member)) {
          continue;
        }

        if (!JavaPsiFacade.getInstance(myPlace.getProject()).getResolveHelper().isAccessible(member, myPlace, null)) {
          continue;
        }

        final LookupElement item = result instanceof PsiMethod ? createMethodElement((PsiMethod) result) : createFieldElement((PsiField) result);
        if (item != null) {
          item.putUserData(EXPECTED_TYPE_MEMBER, isExpectedTypeMember);
          results.accept(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(item));
        }
      }
    }
  }

  @Nullable
  protected abstract LookupElement createFieldElement(PsiField field);

  @Nullable
  protected abstract LookupElement createMethodElement(PsiMethod method);
}
