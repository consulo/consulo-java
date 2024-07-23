/*
 * Copyright 2005-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.abstraction;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class MethodOnlyUsedFromInnerClassInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreMethodsAccessedFromAnonymousClass = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreStaticMethodsFromNonStaticInnerClass = false;

  @SuppressWarnings({"PublicField"})
  public boolean onlyReportStaticMethods = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.methodOnlyUsedFromInnerClassDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    final PsiNamedElement element = (PsiNamedElement)infos[0];
    final String name = element.getName();
    if (infos.length > 1) {
      if (Boolean.TRUE.equals(infos[1])) {
        return InspectionGadgetsLocalize.methodOnlyUsedFromInnerClassProblemDescriptorAnonymousExtending(name).get();
      }
      return InspectionGadgetsLocalize.methodOnlyUsedFromInnerClassProblemDescriptorAnonymousImplementing(name).get();
    }
    return InspectionGadgetsLocalize.methodOnlyUsedFromInnerClassProblemDescriptor(name).get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(
      InspectionGadgetsLocalize.methodOnlyUsedFromInnerClassIgnoreOption().get(),
      "ignoreMethodsAccessedFromAnonymousClass"
    );
    panel.addCheckbox(
      InspectionGadgetsLocalize.ignoreStaticMethodsAccessedFromANonStaticInnerClass().get(),
      "ignoreStaticMethodsFromNonStaticInnerClass"
    );
    panel.addCheckbox(InspectionGadgetsLocalize.onlyReportStaticMethods().get(), "onlyReportStaticMethods");
    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodOnlyUsedFromInnerClassVisitor();
  }

  private class MethodOnlyUsedFromInnerClassVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!method.hasModifierProperty(PsiModifier.PRIVATE) || method.isConstructor()) {
        return;
      }
      if (onlyReportStaticMethods && !method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final MethodReferenceFinder processor = new MethodReferenceFinder(method);
      if (!processor.isOnlyAccessedFromInnerClass()) {
        return;
      }
      final PsiClass containingClass = processor.getContainingClass();
      if (ignoreStaticMethodsFromNonStaticInnerClass && method.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiElement parent = containingClass.getParent();
        if (parent instanceof PsiClass && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
      }
      if (containingClass instanceof PsiAnonymousClass) {
        final PsiClass[] interfaces = containingClass.getInterfaces();
        final PsiClass superClass;
        if (interfaces.length == 1) {
          superClass = interfaces[0];
          registerMethodError(method, superClass, Boolean.valueOf(false));
        }
        else {
          superClass = containingClass.getSuperClass();
          if (superClass == null) {
            return;
          }
          registerMethodError(method, superClass, Boolean.valueOf(true));
        }
      }
      else {
        registerMethodError(method, containingClass);
      }
    }
  }

  private class MethodReferenceFinder implements Processor<PsiReference> {

    private final PsiClass methodClass;
    private final PsiMethod method;
    private boolean onlyAccessedFromInnerClass = false;

    private PsiClass cache = null;

    MethodReferenceFinder(@Nonnull PsiMethod method) {
      this.method = method;
      methodClass = method.getContainingClass();
    }

    @Override
    public boolean process(PsiReference reference) {
      final PsiElement element = reference.getElement();
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method.equals(containingMethod)) {
        return true;
      }
      final PsiClass containingClass = ClassUtils.getContainingClass(element);
      if (containingClass == null) {
        onlyAccessedFromInnerClass = false;
        return false;
      }
      if (containingClass instanceof PsiAnonymousClass) {
        final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)containingClass;
        final PsiExpressionList argumentList = anonymousClass.getArgumentList();
        if (PsiTreeUtil.isAncestor(argumentList, element, true)) {
          onlyAccessedFromInnerClass = false;
          return false;
        }
        if (ignoreMethodsAccessedFromAnonymousClass) {
          onlyAccessedFromInnerClass = false;
          return false;
        }
      }
      if (cache != null) {
        if (!cache.equals(containingClass)) {
          onlyAccessedFromInnerClass = false;
          return false;
        }
      }
      else if (!PsiTreeUtil.isAncestor(methodClass, containingClass, true)) {
        onlyAccessedFromInnerClass = false;
        return false;
      }
      onlyAccessedFromInnerClass = true;
      cache = containingClass;
      return true;
    }

    public boolean isOnlyAccessedFromInnerClass() {
      final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(method.getProject());
      final ProgressManager progressManager = ProgressManager.getInstance();
      final ProgressIndicator progressIndicator = progressManager.getProgressIndicator();
      final PsiSearchHelper.SearchCostResult searchCost =
        searchHelper.isCheapEnoughToSearch(method.getName(), method.getResolveScope(), null, progressIndicator);
      if (searchCost == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES ||
          searchCost == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) {
        return onlyAccessedFromInnerClass;
      }
      final Query<PsiReference> query = ReferencesSearch.search(method);
      query.forEach(this);
      return onlyAccessedFromInnerClass;
    }

    public PsiClass getContainingClass() {
      return cache;
    }
  }
}