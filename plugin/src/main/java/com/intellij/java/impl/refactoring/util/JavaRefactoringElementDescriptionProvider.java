/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.util;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.util.RefactoringDescriptionLocation;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.ElementDescriptionProvider;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaRefactoringElementDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(@Nonnull final PsiElement element, @Nonnull final ElementDescriptionLocation location) {
    if (!(location instanceof RefactoringDescriptionLocation)) return null;
    RefactoringDescriptionLocation rdLocation = (RefactoringDescriptionLocation) location;

    if (element instanceof PsiField field) {
      int options = PsiFormatUtil.SHOW_NAME;
      if (rdLocation.includeParent()) {
        options |= PsiFormatUtil.SHOW_CONTAINING_CLASS;
      }
      String fieldEmphasized = CommonRefactoringUtil.htmlEmphasize(PsiFormatUtil.formatVariable(field, options, PsiSubstitutor.EMPTY));
      return RefactoringLocalize.fieldDescription(fieldEmphasized).get();
    }

    if (element instanceof PsiMethod method) {
      int options = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS;
      if (rdLocation.includeParent()) {
        options |= PsiFormatUtil.SHOW_CONTAINING_CLASS;
      }
      String empasizedMethod =
        CommonRefactoringUtil.htmlEmphasize(PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, PsiFormatUtil.SHOW_TYPE));
      return method.isConstructor()
        ? RefactoringLocalize.constructorDescription(empasizedMethod).get()
        : RefactoringLocalize.methodDescription(empasizedMethod).get();
    }

    if (element instanceof PsiClassInitializer) {
      PsiClassInitializer initializer = (PsiClassInitializer) element;
      boolean isStatic = initializer.hasModifierProperty(PsiModifier.STATIC);
      String elementDescription = getElementDescription(initializer.getContainingClass(), RefactoringDescriptionLocation.WITHOUT_PARENT);
      return isStatic
        ? RefactoringLocalize.staticInitializerDescription(elementDescription).get()
        : RefactoringLocalize.instanceInitializerDescription(elementDescription).get();
    }

    if (element instanceof PsiParameter parameter) {
      String paramNameEmpasized = CommonRefactoringUtil.htmlEmphasize(parameter.getName());
      return parameter.getDeclarationScope() instanceof PsiForeachStatement
        ? RefactoringLocalize.localVariableDescription(paramNameEmpasized).get()
        : RefactoringLocalize.parameterDescription(paramNameEmpasized).get();
    }

    if (element instanceof PsiLocalVariable localVariable) {
      return RefactoringLocalize.localVariableDescription(CommonRefactoringUtil.htmlEmphasize(localVariable.getName())).get();
    }

    if (element instanceof PsiJavaPackage javaPackage) {
      return RefactoringLocalize.packageDescription(CommonRefactoringUtil.htmlEmphasize(javaPackage.getName())).get();
    }

    if (element instanceof PsiClass psiClass) {
      //TODO : local & anonymous
      return RefactoringLocalize.classDescription(CommonRefactoringUtil.htmlEmphasize(DescriptiveNameUtil.getDescriptiveName(psiClass))).get();
    }
    return null;
  }
}
