// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.java.impl.JavaBundle;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

/**
 * @author Dmitry Avdeev
 */
@ExtensionImpl
public final class DefaultAnnotationParamInspection extends AbstractBaseJavaLocalInspectionTool<Object> {
  @Nonnull
  @Override
  public String getDisplayName() {
    return JavaBundle.message("inspection.default.annotation.param");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesDeclarationRedundancy().get();
  }

  @Nonnull
  @Override
  public String getShortName() {
    return "DefaultAnnotationParam";
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitorImpl(
    @Nonnull ProblemsHolder holder,
    boolean isOnTheFly,
    LocalInspectionToolSession session,
    Object o
  ) {
    return new JavaElementVisitor() {
      @Override
      @RequiredReadAction
      public void visitNameValuePair(final @Nonnull PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        PsiReference reference = pair.getReference();
        if (reference == null) return;
        PsiElement element = reference.resolve();
        if (!(element instanceof PsiAnnotationMethod)) return;

        PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)element).getDefaultValue();
        if (defaultValue == null) return;

        if (AnnotationUtil.equal(value, defaultValue)) {
          PsiElement elementParent = element.getParent();
          if (elementParent instanceof PsiClass psiClass) {
            final String qualifiedName = psiClass.getQualifiedName();
            final String name = ((PsiAnnotationMethod)element).getName();
            if (ContainerUtil.exists(
              Application.get().getExtensionList(DefaultAnnotationParamIgnoreFilter.class),
              ext -> ext.ignoreAnnotationParam(qualifiedName, name)
            )) {
              return;
            }
          }
          holder.registerProblem(
            value,
            JavaBundle.message("inspection.message.redundant.default.parameter.value.assignment"),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            createRemoveParameterFix(value)
          );
        }
      }
    };
  }

  @Nonnull
  private static LocalQuickFix createRemoveParameterFix(PsiAnnotationMemberValue value) {
    return new LocalQuickFixAndIntentionActionOnPsiElement(value) {
      @Nonnull
      @Override
      public String getText() {
        return JavaBundle.message("quickfix.family.remove.redundant.parameter");
      }

      @Override
      public void invoke(
        @Nonnull Project project,
        @Nonnull PsiFile psiFile,
        @Nullable Editor editor,
        @Nonnull PsiElement psiElement,
        @Nonnull PsiElement psiElement1
      ) {
        psiElement.getParent().delete();
      }

      @Nls
      @Nonnull
      @Override
      public String getFamilyName() {
        return JavaBundle.message("quickfix.family.remove.redundant.parameter");
      }
    };
  }
}
