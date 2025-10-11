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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ExtendsObjectInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.extendsObjectDisplayName();
  }

  @Override
  @Nonnull
  public String getID() {
    return "ClassExplicitlyExtendsObject";
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.extendsObjectProblemDescriptor().get();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ExtendsObjectFix();
  }

  private static class ExtendsObjectFix extends InspectionGadgetsFix {
    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.extendsObjectRemoveQuickfix();
    }

    @Override
    public void doFix(@Nonnull Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement extendClassIdentifier = descriptor.getPsiElement();
      final PsiClass element =
        (PsiClass)extendClassIdentifier.getParent();
      if (element == null) {
        return;
      }
      final PsiReferenceList extendsList = element.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements =
        extendsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement :
        referenceElements) {
        deleteElement(referenceElement);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsObjectVisitor();
  }

  private static class ExtendsObjectVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      final PsiClassType[] types = aClass.getExtendsListTypes();
      for (final PsiClassType type : types) {
        if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          registerClassError(aClass);
        }
      }
    }
  }
}