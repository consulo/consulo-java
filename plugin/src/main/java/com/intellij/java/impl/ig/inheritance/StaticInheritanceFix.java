/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.util.query.Query;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.impl.DebugUtil;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;

import javax.annotation.Nonnull;

/**
 * User: cdr
 */
class StaticInheritanceFix extends InspectionGadgetsFix {
  private static final Logger LOG = Logger.getInstance(StaticInheritanceFix.class);
  private final boolean myReplaceInWholeProject;

  StaticInheritanceFix(boolean replaceInWholeProject) {
    myReplaceInWholeProject = replaceInWholeProject;
  }

  @Nonnull
  public String getName() {
    String scope =
      myReplaceInWholeProject ? InspectionGadgetsBundle.message("the.whole.project") : InspectionGadgetsBundle.message("this.class");
    return InspectionGadgetsBundle.message("static.inheritance.replace.quickfix", scope);
  }

  public void doFix(final Project project, final ProblemDescriptor descriptor) throws IncorrectOperationException {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        dodoFix(project, descriptor);
      }
    }, ApplicationManager.getApplication().getNoneModalityState(), project.getDisposed());
  }

  private void dodoFix(final Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)descriptor.getPsiElement();
    final PsiClass iface = (PsiClass)referenceElement.resolve();
    assert iface != null;
    final PsiField[] allFields = iface.getAllFields();

    final PsiClass implementingClass = ClassUtils.getContainingClass(referenceElement);
    final PsiManager manager = referenceElement.getManager();
    assert implementingClass != null;
    final PsiFile file = implementingClass.getContainingFile();

    ProgressManager.getInstance().run(new Task.Modal(project, "Replacing usages of " + iface.getName(), false) {

      public void run(@Nonnull ProgressIndicator indicator) {
        for (final PsiField field : allFields) {
          final Query<PsiReference> search = ReferencesSearch.search(field, implementingClass.getUseScope(), false);
          for (PsiReference reference : search) {
            if (!(reference instanceof PsiReferenceExpression)) {
              continue;
            }
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)reference;
            if (!myReplaceInWholeProject) {
              PsiClass aClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
              boolean isInheritor = false;
              while (aClass != null) {
                isInheritor = InheritanceUtil.isInheritorOrSelf(aClass, implementingClass, true);
                if (isInheritor) break;
                aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
              }
              if (!isInheritor) continue;
            }
            final Runnable runnable = new Runnable() {
              public void run() {
                if (isQuickFixOnReadOnlyFile(referenceExpression)) {
                  return;
                }
                final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
                final PsiReferenceExpression qualified =
                  (PsiReferenceExpression)elementFactory
                    .createExpressionFromText("xxx." + referenceExpression.getText(), referenceExpression);
                final PsiReferenceExpression newReference = (PsiReferenceExpression)referenceExpression.replace(qualified);
                final PsiReferenceExpression qualifier = (PsiReferenceExpression)newReference.getQualifierExpression();
                assert qualifier != null : DebugUtil.psiToString(newReference, false);
                final PsiClass containingClass = field.getContainingClass();
                qualifier.bindToElement(containingClass);
              }
            };
            invokeWriteAction(runnable, file);
          }
        }
        final Runnable runnable = new Runnable() {
          public void run() {
            PsiClassType classType = JavaPsiFacade.getInstance(project).getElementFactory().createType(iface);
            IntentionAction fix = QuickFixFactory.getInstance().createExtendsListFix(implementingClass, classType, false);
            fix.invoke(project, null, file);
          }
        };
        invokeWriteAction(runnable, file);
      }
    });
  }

  private static void invokeWriteAction(final Runnable runnable, final PsiFile file) {
    Application.get().invokeAndWait(new Runnable() {
      public void run() {
        new WriteCommandAction(file.getProject(), file) {
          protected void run(Result result) throws Throwable {
            runnable.run();
          }
        }.execute();
      }
    });
  }
}
