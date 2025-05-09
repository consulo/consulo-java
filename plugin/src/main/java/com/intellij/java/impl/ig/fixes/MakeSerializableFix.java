/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class MakeSerializableFix extends InspectionGadgetsFix {

  @Nonnull
  public String getName() {
    return InspectionGadgetsBundle.message(
      "make.class.serializable.quickfix");
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    final PsiElement nameElement = descriptor.getPsiElement();
    final PsiClass containingClass =
      ClassUtils.getContainingClass(nameElement);
    assert containingClass != null;
    final PsiElementFactory elementFactory =
      JavaPsiFacade.getElementFactory(project);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiJavaCodeReferenceElement referenceElement =
      elementFactory.createReferenceElementByFQClassName(CommonClassNames.JAVA_IO_SERIALIZABLE, scope);
    final PsiReferenceList implementsList =
      containingClass.getImplementsList();
    assert implementsList != null;
    implementsList.add(referenceElement);
  }
}