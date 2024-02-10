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

import jakarta.annotation.Nonnull;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.java.language.module.util.JavaClassNames;

@ExtensionImpl
public class TypeParameterExtendsObjectInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "type.parameter.extends.object.display.name");
  }

  @Override
  @Nonnull
  public String getID() {
    return "TypeParameterExplicitlyExtendsObject";
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    final Integer type = (Integer)infos[0];
    if (type.intValue() == 1) {
      return InspectionGadgetsBundle.message(
        "type.parameter.extends.object.problem.descriptor1");
    }
    else {
      return InspectionGadgetsBundle.message(
        "type.parameter.extends.object.problem.descriptor2");
    }
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
    public String getName() {
      return InspectionGadgetsBundle.message(
        "extends.object.remove.quickfix");
    }

    @Override
    public void doFix(@Nonnull Project project,
                      ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement identifier = descriptor.getPsiElement();
      final PsiElement parent = identifier.getParent();
      if (parent instanceof PsiTypeParameter) {
        final PsiTypeParameter typeParameter =
          (PsiTypeParameter)parent;
        final PsiReferenceList extendsList =
          typeParameter.getExtendsList();
        final PsiJavaCodeReferenceElement[] referenceElements =
          extendsList.getReferenceElements();
        for (PsiJavaCodeReferenceElement referenceElement :
          referenceElements) {
          deleteElement(referenceElement);
        }
      }
      else {
        final PsiTypeElement typeElement = (PsiTypeElement)parent;
        PsiElement child = typeElement.getLastChild();
        while (child != null) {
          if (child instanceof PsiJavaToken) {
            final PsiJavaToken javaToken = (PsiJavaToken)child;
            final IElementType tokenType = javaToken.getTokenType();
            if (tokenType == JavaTokenType.QUEST) {
              return;
            }
          }
          child.delete();
          child = typeElement.getLastChild();
        }
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsObjectVisitor();
  }

  private static class ExtendsObjectVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeParameter(PsiTypeParameter parameter) {
      super.visitTypeParameter(parameter);
      final PsiClassType[] extendsListTypes =
        parameter.getExtendsListTypes();
      if (extendsListTypes.length != 1) {
        return;
      }
      final PsiClassType extendsType = extendsListTypes[0];
      if (!extendsType.equalsToText(JavaClassNames.JAVA_LANG_OBJECT)) {
        return;
      }
      final PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      registerError(nameIdentifier, Integer.valueOf(1));
    }


    @Override
    public void visitTypeElement(PsiTypeElement typeElement) {
      super.visitTypeElement(typeElement);
      final PsiElement lastChild = typeElement.getLastChild();
      if (!(lastChild instanceof PsiTypeElement)) {
        return;
      }
      final PsiType type = typeElement.getType();
      if (!(type instanceof PsiWildcardType)) {
        return;
      }
      final PsiWildcardType wildcardType = (PsiWildcardType)type;
      if (!wildcardType.isExtends()) {
        return;
      }
      final PsiType extendsBound = wildcardType.getBound();
      if (!TypeUtils.isJavaLangObject(extendsBound)) {
        return;
      }
      final PsiElement firstChild = typeElement.getFirstChild();
      if (firstChild == null) {
        return;
      }
      registerError(firstChild, Integer.valueOf(2));
    }
  }
}