/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class MarkerInterfaceInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.markerInterfaceDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.markerInterfaceProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MarkerInterfaceVisitor();
  }

  private static class MarkerInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (!aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      final PsiField[] fields = aClass.getFields();
      if (fields.length != 0) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      if (methods.length != 0) {
        return;
      }
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList != null) {
        final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
        if (referenceElements.length > 0) {
          if (referenceElements.length > 1) {
            return;
          }
          final PsiReferenceParameterList parameterList = referenceElements[0].getParameterList();
          if (parameterList == null) {
            return;
          }
          final PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
          if (typeParameterElements.length != 0) {
            return;
          }
        }
      }
      registerClassError(aClass);
    }
  }
}