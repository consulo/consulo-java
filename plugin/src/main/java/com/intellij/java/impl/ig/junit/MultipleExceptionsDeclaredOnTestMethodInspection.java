/*
 * Copyright 2009-2010 Bas Leijdekkers
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

import jakarta.annotation.Nonnull;

import consulo.annotation.component.ExtensionImpl;
import org.jetbrains.annotations.Nls;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.java.language.module.util.JavaClassNames;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiReference;
import com.intellij.java.language.psi.PsiReferenceList;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.application.util.query.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;

@ExtensionImpl
public class MultipleExceptionsDeclaredOnTestMethodInspection
  extends BaseInspection {

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "multiple.exceptions.declared.on.test.method.display.name");
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "multiple.exceptions.declared.on.test.method.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MultipleExceptionsDeclaredOnTestMethodFix();
  }

  private static class MultipleExceptionsDeclaredOnTestMethodFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "multiple.exceptions.declared.on.test.method.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiReferenceList)) {
        return;
      }
      final PsiReferenceList referenceList = (PsiReferenceList)element;
      final PsiJavaCodeReferenceElement[] referenceElements =
        referenceList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        referenceElement.delete();
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(
        project);
      final GlobalSearchScope scope = referenceList.getResolveScope();
      final PsiJavaCodeReferenceElement referenceElement =
        factory.createReferenceElementByFQClassName(
          JavaClassNames.JAVA_LANG_EXCEPTION, scope);
      referenceList.add(referenceElement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantExceptionDeclarationVisitor();
  }

  private static class RedundantExceptionDeclarationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!TestUtils.isJUnitTestMethod(method)) {
        return;
      }
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiJavaCodeReferenceElement[] referenceElements =
        throwsList.getReferenceElements();
      if (referenceElements.length < 2) {
        return;
      }

      final Query<PsiReference> query =
        MethodReferencesSearch.search(method);
      final PsiReference firstReference = query.findFirst();
      if (firstReference != null) {
        return;
      }
      registerError(throwsList);
    }
  }
}