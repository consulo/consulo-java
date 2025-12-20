/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class UseOfObsoleteAssertInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.usageOfObsoleteAssertDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.useOfObsoleteAssertProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceObsoleteAssertsFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UseOfObsoleteAssertVisitor();
  }

  private static class UseOfObsoleteAssertVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      Project project = expression.getProject();
      Module module = ModuleUtil.findModuleForPsiElement(expression);
      if (module == null) {
        return;
      }
      PsiClass newAssertClass = JavaPsiFacade.getInstance(project)
        .findClass("org.junit.Assert", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
      if (newAssertClass == null) {
        return;
      }
      PsiMethod psiMethod = expression.resolveMethod();
      if (psiMethod == null || !psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass != null && Comparing.strEqual(containingClass.getQualifiedName(), "junit.framework.Assert")) {
        registerMethodCallError(expression);
      }
    }
  }

  private static class ReplaceObsoleteAssertsFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiElement psiElement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      if (!(psiElement instanceof PsiMethodCallExpression)) {
        return;
      }
      PsiClass newAssertClass =
        JavaPsiFacade.getInstance(project).findClass("org.junit.Assert", GlobalSearchScope.allScope(project));
      PsiClass oldAssertClass =
        JavaPsiFacade.getInstance(project).findClass("junit.framework.Assert", GlobalSearchScope.allScope(project));

      if (newAssertClass == null) {
        return;
      }
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)psiElement;
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      PsiElement usedImport = qualifierExpression instanceof PsiReferenceExpression ?
                                    ((PsiReferenceExpression)qualifierExpression).advancedResolve(true).getCurrentFileResolveScope() :
                                    methodExpression.advancedResolve(true).getCurrentFileResolveScope();
      PsiMethod psiMethod = methodCallExpression.resolveMethod();

      boolean isImportUnused = isImportBecomeUnused(methodCallExpression, usedImport, psiMethod);

      PsiImportStaticStatement staticStatement = null;
      if (qualifierExpression == null) {
        staticStatement = staticallyImported(oldAssertClass, methodExpression);
      }

      JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      if (staticStatement == null) {
        methodExpression.setQualifierExpression(JavaPsiFacade.getElementFactory(project).createReferenceExpression(newAssertClass));

        if (isImportUnused && usedImport instanceof PsiImportStatementBase) {
          usedImport.delete();
        }

        styleManager.shortenClassReferences(methodExpression);
      }
      else {
        if (isImportUnused) {
          PsiJavaCodeReferenceElement importReference = staticStatement.getImportReference();
          if (importReference != null) {
            if (staticStatement.isOnDemand()) {
              importReference.bindToElement(newAssertClass);
            }
            else {
              PsiElement importQExpression = importReference.getQualifier();
              if (importQExpression instanceof PsiReferenceExpression) {
                ((PsiReferenceExpression)importQExpression).bindToElement(newAssertClass);
              }
            }
          }
        }
        else {
          methodExpression
            .setQualifierExpression(JavaPsiFacade.getElementFactory(project).createReferenceExpression(newAssertClass));
          styleManager.shortenClassReferences(methodExpression);
        }
      }
      /*
          //refs can be optimized now but should we really?
          if (isImportUnused) {
            for (PsiReference reference : ReferencesSearch.search(newAssertClass, new LocalSearchScope(methodCallExpression.getContainingFile()))) {
              final PsiElement element = reference.getElement();
              styleManager.shortenClassReferences(element);
            }
          }*/
    }

    private static boolean isImportBecomeUnused(final PsiMethodCallExpression methodCallExpression,
                                                final PsiElement usedImport,
                                                final PsiMethod psiMethod) {
      final boolean[] proceed = new boolean[]{true};
      methodCallExpression.getContainingFile().accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (proceed[0]) {
            super.visitElement(element);
          }
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          if (expression == methodCallExpression) {
            return;
          }
          PsiMethod resolved = expression.resolveMethod();
          if (resolved == psiMethod) {
            proceed[0] = false;
          }
          else {
            PsiElement resolveScope =
              expression.getMethodExpression().advancedResolve(false).getCurrentFileResolveScope();
            if (resolveScope == usedImport) {
              proceed[0] = false;
            }
          }
        }
      });
      return proceed[0];
    }

    @Nullable
    private static PsiImportStaticStatement staticallyImported(PsiClass oldAssertClass, PsiReferenceExpression methodExpression) {
      String referenceName = methodExpression.getReferenceName();
      PsiFile containingFile = methodExpression.getContainingFile();
      if (!(containingFile instanceof PsiJavaFile)) {
        return null;
      }
      PsiImportList importList = ((PsiJavaFile)containingFile).getImportList();
      if (importList == null) {
        return null;
      }
      PsiImportStaticStatement[] statements = importList.getImportStaticStatements();
      for (PsiImportStaticStatement statement : statements) {
        if (oldAssertClass != statement.resolveTargetClass()) {
          continue;
        }
        String importRefName = statement.getReferenceName();
        PsiJavaCodeReferenceElement importReference = statement.getImportReference();
        if (importReference == null) {
          continue;
        }
        if (Comparing.strEqual(importRefName, referenceName)) {
          PsiElement qualifier = importReference.getQualifier();
          if (qualifier instanceof PsiJavaCodeReferenceElement) {
            return statement;
          }
        }
        else if (importRefName == null) {
          return statement;
        }
      }
      return null;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.useOfObsoleteAssertQuickfix();
    }
  }
}