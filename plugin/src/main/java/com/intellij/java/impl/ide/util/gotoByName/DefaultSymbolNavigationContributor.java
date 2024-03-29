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
package com.intellij.java.impl.ide.util.gotoByName;

import com.intellij.java.language.impl.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.ide.navigation.GotoSymbolContributor;
import consulo.language.editor.ui.DefaultPsiElementCellRenderer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FindSymbolParameters;
import consulo.language.psi.stub.IdFilter;
import consulo.logging.Logger;
import consulo.navigation.NavigationItem;
import consulo.project.content.scope.ProjectAwareSearchScope;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@ExtensionImpl
public class DefaultSymbolNavigationContributor implements GotoSymbolContributor {
  private static final Logger LOGGER = Logger.getInstance(DefaultSymbolNavigationContributor.class);

  private static boolean isOpenable(PsiMember member) {
    return member.getContainingFile().getVirtualFile() != null;
  }

  private static boolean hasSuperMethod(PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }

    for (PsiMethod candidate : containingClass.findMethodsByName(method.getName(), true)) {
      if (candidate.getContainingClass() != containingClass && PsiSuperMethodImplUtil.isSuperMethodSmart(method, candidate)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void processNames(@Nonnull Processor<String> processor, @Nonnull SearchScope scope, @Nullable IdFilter filter) {
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(((ProjectAwareSearchScope) scope).getProject());
    cache.processAllClassNames(processor, (GlobalSearchScope) scope, filter);
    cache.processAllFieldNames(processor, (GlobalSearchScope) scope, filter);
    cache.processAllMethodNames(processor, (GlobalSearchScope) scope, filter);
  }

  @Override
  public void processElementsWithName(@Nonnull String name, @Nonnull final Processor<NavigationItem> processor,
                                      @Nonnull FindSymbolParameters parameters) {

    GlobalSearchScope scope = (GlobalSearchScope) parameters.getSearchScope();
    IdFilter filter = parameters.getIdFilter();
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(scope.getProject());
    //noinspection UnusedDeclaration
    final Set<PsiMethod> collectedMethods = new HashSet<PsiMethod>();
    boolean success = cache.processFieldsWithName(name, new Processor<PsiField>() {
      @Override
      public boolean process(PsiField field) {
        if (isOpenable(field)) {
          return processor.process(field);
        }
        return true;
      }
    }, scope, filter) &&
        cache.processClassesWithName(name, new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass aClass) {
            if (isOpenable(aClass)) {
              return processor.process(aClass);
            }
            return true;
          }
        }, scope, filter) &&
        cache.processMethodsWithName(name, new Processor<PsiMethod>() {
          @Override
          public boolean process(PsiMethod method) {
            if (!method.isConstructor() && isOpenable(method)) {
              collectedMethods.add(method);
            }
            return true;
          }
        }, scope, filter);
    if (success) {
      // hashSuperMethod accesses index and can not be invoked without risk of the deadlock in processMethodsWithName
      Iterator<PsiMethod> iterator = collectedMethods.iterator();
      while (iterator.hasNext()) {
        PsiMethod method = iterator.next();
        if (!hasSuperMethod(method) && !processor.process(method)) {
          return;
        }
        ProgressManager.checkCanceled();
        iterator.remove();
      }
    }
  }

  private static class MyComparator implements Comparator<PsiModifierListOwner> {
    public static final MyComparator INSTANCE = new MyComparator();

    private final DefaultPsiElementCellRenderer myRenderer = new DefaultPsiElementCellRenderer();

    @Override
    public int compare(PsiModifierListOwner element1, PsiModifierListOwner element2) {
      if (element1 == element2) {
        return 0;
      }

      PsiModifierList modifierList1 = element1.getModifierList();
      PsiModifierList modifierList2 = element2.getModifierList();

      int level1 = modifierList1 == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifierList1);
      int level2 = modifierList2 == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifierList2);
      if (level1 != level2) {
        return level2 - level1;
      }

      int kind1 = getElementTypeLevel(element1);
      int kind2 = getElementTypeLevel(element2);
      if (kind1 != kind2) {
        return kind1 - kind2;
      }

      if (element1 instanceof PsiMethod) {
        LOGGER.assertTrue(element2 instanceof PsiMethod);
        PsiParameter[] parms1 = ((PsiMethod) element1).getParameterList().getParameters();
        PsiParameter[] parms2 = ((PsiMethod) element2).getParameterList().getParameters();

        if (parms1.length != parms2.length) {
          return parms1.length - parms2.length;
        }
      }

      String text1 = myRenderer.getElementText(element1);
      String text2 = myRenderer.getElementText(element2);
      if (!text1.equals(text2)) {
        return text1.compareTo(text2);
      }

      String containerText1 = myRenderer.getContainerText(element1, text1);
      String containerText2 = myRenderer.getContainerText(element2, text2);
      if (containerText1 == null) {
        containerText1 = "";
      }
      if (containerText2 == null) {
        containerText2 = "";
      }
      return containerText1.compareTo(containerText2);
    }

    private static int getElementTypeLevel(PsiElement element) {
      if (element instanceof PsiMethod) {
        return 1;
      } else if (element instanceof PsiField) {
        return 2;
      } else if (element instanceof PsiClass) {
        return 3;
      } else {
        LOGGER.error(element);
        return 0;
      }
    }
  }

}
