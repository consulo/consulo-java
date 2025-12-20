/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.visibility;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ClassEscapesItsScopeInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "ClassEscapesDefinedScope";
  }

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.classEscapesDefinedScopeDisplayName();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.classEscapesDefinedScopeProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ClassEscapesItsScopeVisitor();
  }

  private static class ClassEscapesItsScopeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      //no call to super, so we don't drill into anonymous classes
      if (method.isConstructor()) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      PsiType componentType = returnType.getDeepComponentType();
      if (!(componentType instanceof PsiClassType)) {
        return;
      }
      PsiClass returnClass = ((PsiClassType)componentType).resolve();
      if (returnClass == null) {
        return;
      }
      if (returnClass.getParent() instanceof PsiTypeParameterList) {
        return;//if it's a type parameter, it's okay.  Must be a better way to check this.
      }
      if (!isLessRestrictiveScope(method, returnClass)) {
        return;
      }
      PsiTypeElement typeElement = method.getReturnTypeElement();
      if (typeElement == null) {
        return;
      }
      PsiJavaCodeReferenceElement baseTypeElement = typeElement.getInnermostComponentReferenceElement();
      if (baseTypeElement == null) {
        return;
      }
      registerError(baseTypeElement);
    }

    @Override
    public void visitField(@Nonnull PsiField field) {
      //no call to super, so we don't drill into anonymous classes
      if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      PsiType type = field.getType();
      PsiType componentType = type.getDeepComponentType();
      if (!(componentType instanceof PsiClassType)) {
        return;
      }
      PsiClass fieldClass =
        ((PsiClassType)componentType).resolve();
      if (fieldClass == null) {
        return;
      }
      if (!fieldHasLessRestrictiveScope(field, fieldClass)) {
        return;
      }
      PsiTypeElement typeElement = field.getTypeElement();
      if (typeElement == null) {
        return;
      }
      PsiJavaCodeReferenceElement baseTypeElement =
        typeElement.getInnermostComponentReferenceElement();
      if (baseTypeElement == null) {
        return;
      }
      registerError(baseTypeElement);
    }


    private static boolean isLessRestrictiveScope(PsiMethod method, PsiClass aClass) {
      int methodScopeOrder = getScopeOrder(method);
      int classScopeOrder = getScopeOrder(aClass);
      PsiClass containingClass = method.getContainingClass();
      int containingClassScopeOrder =
        getScopeOrder(containingClass);
      if (methodScopeOrder <= classScopeOrder ||
          containingClassScopeOrder <= classScopeOrder) {
        return false;
      }
      PsiMethod[] superMethods = method.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        if (!isLessRestrictiveScope(superMethod, aClass)) {
          return false;
        }
      }
      return true;
    }

    private static boolean fieldHasLessRestrictiveScope(PsiField field, PsiClass aClass) {
      int fieldScopeOrder = getScopeOrder(field);
      PsiClass containingClass = field.getContainingClass();
      int containingClassScopeOrder = getScopeOrder(containingClass);
      int classScopeOrder = getScopeOrder(aClass);
      return fieldScopeOrder > classScopeOrder && containingClassScopeOrder > classScopeOrder;
    }

    private static int getScopeOrder(PsiModifierListOwner element) {
      if (element.hasModifierProperty(PsiModifier.PUBLIC)) {
        return 4;
      }
      else if (element.hasModifierProperty(PsiModifier.PRIVATE)) {
        return 1;
      }
      else if (element.hasModifierProperty(PsiModifier.PROTECTED)) {
        return 2;
      }
      else {
        return 3;
      }
    }
  }
}