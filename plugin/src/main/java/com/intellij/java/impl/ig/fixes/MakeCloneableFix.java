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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class MakeCloneableFix extends InspectionGadgetsFix {

  private final boolean isInterface;

  public MakeCloneableFix(boolean isInterface) {
    this.isInterface = isInterface;
  }

  @Nonnull
  public LocalizeValue getName() {
    if (isInterface) {
      return InspectionGadgetsLocalize.makeInterfaceCloneableQuickfix();
    } else {
      return InspectionGadgetsLocalize.makeClassCloneableQuickfix();
    }
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
    PsiElement nameElement = descriptor.getPsiElement();
    PsiClass containingClass =
        ClassUtils.getContainingClass(nameElement);
    if (containingClass == null) {
      return;
    }
    PsiElementFactory elementFactory =
        JavaPsiFacade.getElementFactory(project);
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    PsiJavaCodeReferenceElement ref =
        elementFactory.createReferenceElementByFQClassName(CommonClassNames.JAVA_LANG_CLONEABLE, scope);
    PsiReferenceList extendsImplementsList;
    if (containingClass.isInterface()) {
      extendsImplementsList = containingClass.getExtendsList();
    } else {
      extendsImplementsList = containingClass.getImplementsList();
    }
    if (extendsImplementsList == null) {
      return;
    }
    extendsImplementsList.add(ref);
  }
}
