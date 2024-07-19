/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.inheritance;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressManager;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiManager;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.List;

@ExtensionImpl
public class AbstractMethodWithMissingImplementationsInspection
  extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.abstractMethodWithMissingImplementationsDisplayName().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.abstractMethodWithMissingImplementationsProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new AbstractMethodWithMissingImplementationsVisitor();
  }

  private static class AbstractMethodWithMissingImplementationsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!containingClass.isInterface() &&
          !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final InheritorFinder inheritorFinder =
        new InheritorFinder(containingClass);
      for (final PsiClass inheritor : inheritorFinder.getInheritors()) {
        if (!inheritor.isInterface() &&
            !inheritor.hasModifierProperty(PsiModifier.ABSTRACT)) {
          if (!hasMatchingImplementation(inheritor, method)) {
            registerMethodError(method);
            return;
          }
        }
      }
    }

    private static boolean hasMatchingImplementation(
      @Nonnull PsiClass aClass,
      @Nonnull PsiMethod method) {
      final PsiMethod overridingMethod =
        findOverridingMethod(aClass, method);
      if (overridingMethod == null ||
          overridingMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      if (!method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        return true;
      }
      final PsiClass superClass = method.getContainingClass();
      final PsiManager manager = overridingMethod.getManager();
      final JavaPsiFacade facade =
        JavaPsiFacade.getInstance(manager.getProject());
      return facade.arePackagesTheSame(superClass, aClass);
    }

    /**
     * @param method the method of which to find an override.
     * @param aClass subclass to find the method in.
     * @return the overriding method.
     */
    @Nullable
    private static PsiMethod findOverridingMethod(
      PsiClass aClass, @Nonnull PsiMethod method) {
      final PsiClass superClass = method.getContainingClass();
      if (aClass.equals(superClass)) {
        return null;
      }
      final PsiSubstitutor substitutor =
        TypeConversionUtil.getSuperClassSubstitutor(superClass,
                                                    aClass, PsiSubstitutor.EMPTY);
      final MethodSignature signature = method.getSignature(substitutor);
      final List<Pair<PsiMethod, PsiSubstitutor>> pairs =
        aClass.findMethodsAndTheirSubstitutorsByName(
          signature.getName(), true);
      for (Pair<PsiMethod, PsiSubstitutor> pair : pairs) {
        final PsiMethod overridingMethod = pair.first;
        if (overridingMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
          continue;
        }
        final PsiClass containingClass =
          overridingMethod.getContainingClass();
        if (containingClass.isInterface()) {
          continue;
        }
        final PsiSubstitutor overridingSubstitutor = pair.second;
        final MethodSignature foundMethodSignature =
          overridingMethod.getSignature(overridingSubstitutor);
        if (MethodSignatureUtil.isSubsignature(signature,
                                               foundMethodSignature) && overridingMethod != method) {
          return overridingMethod;
        }
      }
      return null;
    }
  }

  private static class InheritorFinder implements Runnable {

    private final PsiClass aClass;
    private Collection<PsiClass> inheritors = null;

    InheritorFinder(PsiClass aClass) {
      this.aClass = aClass;
    }

    public void run() {
      final SearchScope searchScope = aClass.getUseScope();
      inheritors = ClassInheritorsSearch.search(aClass, searchScope, true)
        .findAll();
    }

    public Collection<PsiClass> getInheritors() {
      final ProgressManager progressManager =
        ProgressManager.getInstance();
      // do not display progress
      progressManager.runProcess(this, null);
      return inheritors;
    }
  }
}
