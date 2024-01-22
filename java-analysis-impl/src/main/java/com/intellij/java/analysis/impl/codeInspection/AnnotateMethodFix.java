/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiNameValuePair;
import com.intellij.java.language.psi.util.ClassUtil;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressManager;
import consulo.java.analysis.impl.codeInsight.JavaInspectionsBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class AnnotateMethodFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(AnnotateMethodFix.class);

  private final String myAnnotation;
  private final String[] myAnnotationsToRemove;

  public AnnotateMethodFix(@Nonnull String fqn, @Nonnull String... annotationsToRemove) {
    myAnnotation = fqn;
    myAnnotationsToRemove = annotationsToRemove;
    LOG.assertTrue(annotateSelf() || annotateOverriddenMethods(), "annotate method quick fix should not do nothing");
  }

  @Override
  @Nonnull
  public String getName() {
    return getFamilyName() + " " + getPreposition() + " \'@" + ClassUtil.extractClassName(myAnnotation) + "\'";
  }

  @jakarta.annotation.Nonnull
  protected String getPreposition() {
    return "with";
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getFamilyName() {
    if (annotateSelf()) {
      if (annotateOverriddenMethods()) {
        return JavaInspectionsBundle.message("inspection.annotate.overridden.method.and.self.quickfix.family.name");
      } else {
        return JavaInspectionsBundle.message("inspection.annotate.method.quickfix.family.name");
      }
    } else {
      return JavaInspectionsBundle.message("inspection.annotate.overridden.method.quickfix.family.name");
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();

    PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (method == null) {
      return;
    }
    final List<PsiMethod> toAnnotate = new ArrayList<>();
    if (annotateSelf()) {
      toAnnotate.add(method);
    }

    if (annotateOverriddenMethods() && !ProgressManager.getInstance().runProcessWithProgressSynchronously(() ->
    {
      PsiMethod[] methods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
      for (PsiMethod psiMethod : methods) {
        ReadAction.run(() ->
        {
          if (psiMethod.isPhysical() && psiMethod.getManager().isInProject(psiMethod) && AnnotationUtil.isAnnotatingApplicable(psiMethod, myAnnotation) && !AnnotationUtil.isAnnotated
              (psiMethod, myAnnotation, false, false, true)) {
            toAnnotate.add(psiMethod);
          }
        });
      }
    }, "Searching for Overriding Methods", true, project)) {
      return;
    }

    FileModificationService.getInstance().preparePsiElementsForWrite(toAnnotate);
    for (PsiMethod psiMethod : toAnnotate) {
      annotateMethod(psiMethod);
    }
    LanguageUndoUtil.markPsiFileForUndo(method.getContainingFile());
  }

  protected boolean annotateOverriddenMethods() {
    return false;
  }

  protected boolean annotateSelf() {
    return true;
  }

  private void annotateMethod(@Nonnull PsiMethod method) {
    AddAnnotationPsiFix fix = new AddAnnotationPsiFix(myAnnotation, method, PsiNameValuePair.EMPTY_ARRAY, myAnnotationsToRemove);
    fix.invoke(method.getProject(), method.getContainingFile(), method, method);
  }
}
