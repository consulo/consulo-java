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
package com.intellij.java.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.GroupNames;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.logging.Logger;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;

/**
 * User: anna
 * Date: 1/28/11
 */
public class PossibleHeapPollutionVarargsInspection extends BaseJavaBatchLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(PossibleHeapPollutionVarargsInspection.class);

  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "Possible heap pollution from parameterized vararg type";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  @Override
  public String getShortName() {
    return "SafeVarargsDetector";
  }

  @Nonnull
  @Override
  public String getID() {
    return "unchecked";
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly) {
    return new HeapPollutionVisitor() {
      @Override
      protected void registerProblem(PsiMethod method, PsiIdentifier nameIdentifier) {
        final LocalQuickFix quickFix;
        if (method.hasModifierProperty(PsiModifier.FINAL) ||
            method.hasModifierProperty(PsiModifier.STATIC) ||
            method.isConstructor()) {
          quickFix = new AnnotateAsSafeVarargsQuickFix();
        } else {
          final PsiClass containingClass = method.getContainingClass();
          LOG.assertTrue(containingClass != null);
          boolean canBeFinal = !method.hasModifierProperty(PsiModifier.ABSTRACT) &&
              !containingClass.isInterface() &&
              OverridingMethodsSearch.search(method).findFirst() == null;
          quickFix = canBeFinal ? new MakeFinalAndAnnotateQuickFix() : null;
        }
        holder.registerProblem(nameIdentifier, "Possible heap pollution from parameterized vararg type #loc", quickFix);
      }
    };
  }

  private static class AnnotateAsSafeVarargsQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public String getName() {
      return "Annotate as @SafeVarargs";
    }

    @Nonnull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiIdentifier) {
        final PsiMethod psiMethod = (PsiMethod) psiElement.getParent();
        if (psiMethod != null) {
          new AddAnnotationPsiFix("java.lang.SafeVarargs", psiMethod, PsiNameValuePair.EMPTY_ARRAY).applyFix(project, descriptor);
        }
      }
    }
  }

  private static class MakeFinalAndAnnotateQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public String getName() {
      return "Make final and annotate as @SafeVarargs";
    }

    @Nonnull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiIdentifier) {
        final PsiMethod psiMethod = (PsiMethod) psiElement.getParent();
        psiMethod.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        new AddAnnotationPsiFix("java.lang.SafeVarargs", psiMethod, PsiNameValuePair.EMPTY_ARRAY).applyFix(project, descriptor);
      }
    }
  }

  public abstract static class HeapPollutionVisitor extends JavaElementVisitor {
    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!PsiUtil.getLanguageLevel(method).isAtLeast(LanguageLevel.JDK_1_7)) return;
      if (AnnotationUtil.isAnnotated(method, "java.lang.SafeVarargs", false)) return;
      if (!method.isVarArgs()) return;

      final PsiParameter psiParameter = method.getParameterList().getParameters()[method.getParameterList().getParametersCount() - 1];
      final PsiType componentType = ((PsiEllipsisType) psiParameter.getType()).getComponentType();
      if (JavaGenericsUtil.isReifiableType(componentType)) {
        return;
      }
      for (PsiReference reference : ReferencesSearch.search(psiParameter)) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiExpression && !PsiUtil.isAccessedForReading((PsiExpression) element)) {
          return;
        }
      }
      final PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier != null) {
        //if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return;
        //final PsiClass containingClass = method.getContainingClass();
        //if (containingClass == null || containingClass.isInterface()) return; do not add
        registerProblem(method, nameIdentifier);
      }
    }

    protected abstract void registerProblem(PsiMethod method, PsiIdentifier nameIdentifier);
  }
}
