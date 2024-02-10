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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.StaticImportMethodFix;
import com.intellij.java.indexing.impl.stubs.index.JavaStaticMemberNameIndex;
import com.intellij.java.language.psi.*;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionService;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.ui.ex.action.IdeActions;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;
import consulo.util.lang.function.PairConsumer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static consulo.util.collection.ContainerUtil.addIfNotNull;

/**
 * @author peter
 */
public abstract class StaticMemberProcessor {
  private final Set<PsiClass> myStaticImportedClasses = new HashSet<>();
  private final PsiElement myPosition;
  private final Project myProject;
  private final PsiResolveHelper myResolveHelper;
  private boolean myHintShown = false;
  private final boolean myPackagedContext;

  public StaticMemberProcessor(final PsiElement position) {
    myPosition = position;
    myProject = myPosition.getProject();
    myResolveHelper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
    myPackagedContext = JavaCompletionUtil.inSomePackage(position);
  }

  public void importMembersOf(@Nullable PsiClass psiClass) {
    addIfNotNull(myStaticImportedClasses, psiClass);
  }

  public void processStaticMethodsGlobally(final PrefixMatcher matcher, Consumer<LookupElement> consumer) {
    final GlobalSearchScope scope = myPosition.getResolveScope();
    Collection<String> memberNames = JavaStaticMemberNameIndex.getInstance().getAllKeys(myProject);
    for (final String memberName : matcher.sortMatching(memberNames)) {
      Set<PsiClass> classes = new HashSet<PsiClass>();
      for (final PsiMember member : JavaStaticMemberNameIndex.getInstance().getStaticMembers(memberName, myProject, scope)) {
        if (isStaticallyImportable(member)) {
          final PsiClass containingClass = member.getContainingClass();
          assert containingClass != null : member.getName() + "; " + member + "; " + member.getClass();

          if (JavaCompletionUtil.isSourceLevelAccessible(myPosition, containingClass, myPackagedContext)) {
            final boolean shouldImport = myStaticImportedClasses.contains(containingClass);
            showHint(shouldImport);
            if (member instanceof PsiMethod && classes.add(containingClass)) {
              final PsiMethod[] allMethods = containingClass.getAllMethods();
              final List<PsiMethod> overloads = ContainerUtil.findAll(allMethods, new Condition<PsiMethod>() {
                @Override
                public boolean value(PsiMethod psiMethod) {
                  return memberName.equals(psiMethod.getName()) && isStaticallyImportable(psiMethod);
                }
              });

              assert !overloads.isEmpty();
              if (overloads.size() == 1) {
                assert member == overloads.get(0);
                consumer.accept(createLookupElement(member, containingClass, shouldImport));
              } else {
                if (overloads.get(0).getParameterList().getParametersCount() == 0) {
                  overloads.add(0, overloads.remove(1));
                }
                consumer.accept(createLookupElement(overloads, containingClass, shouldImport));
              }
            } else if (member instanceof PsiField) {
              consumer.accept(createLookupElement(member, containingClass, shouldImport));
            }
          }
        }
      }
    }
  }

  private void showHint(boolean shouldImport) {
    if (!myHintShown && !shouldImport) {
      final String shortcut = CompletionContributor.getActionShortcut(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
      if (shortcut != null) {
        CompletionService.getCompletionService().setAdvertisementText("To import a method statically, press " + shortcut);
      }
      myHintShown = true;
    }
  }

  public List<PsiMember> processMembersOfRegisteredClasses(final PrefixMatcher matcher, PairConsumer<PsiMember, PsiClass> consumer) {
    final ArrayList<PsiMember> result = ContainerUtil.newArrayList();
    for (final PsiClass psiClass : myStaticImportedClasses) {
      for (final PsiMethod method : psiClass.getAllMethods()) {
        if (matcher.prefixMatches(method.getName())) {
          if (isStaticallyImportable(method)) {
            consumer.consume(method, psiClass);
          }
        }
      }
      for (final PsiField field : psiClass.getAllFields()) {
        if (matcher.prefixMatches(field.getName())) {
          if (isStaticallyImportable(field)) {
            consumer.consume(field, psiClass);
          }
        }
      }
    }
    return result;
  }


  private boolean isStaticallyImportable(final PsiMember member) {
    return member.hasModifierProperty(PsiModifier.STATIC) && isAccessible(member) && !StaticImportMethodFix.isExcluded(member);
  }

  protected boolean isAccessible(PsiMember member) {
    return myResolveHelper.isAccessible(member, myPosition, null);
  }

  @Nonnull
  protected abstract LookupElement createLookupElement(@Nonnull PsiMember member, @Nonnull PsiClass containingClass, boolean shouldImport);

  protected abstract LookupElement createLookupElement(@Nonnull List<PsiMethod> overloads, @Nonnull PsiClass containingClass, boolean shouldImport);
}
