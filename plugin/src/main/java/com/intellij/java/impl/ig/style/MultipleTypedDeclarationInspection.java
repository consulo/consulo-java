/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.fixes.NormalizeDeclarationFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class MultipleTypedDeclarationInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.multipleTypedDeclarationDisplayName();
  }

  @Nonnull
  public String getID() {
    return "VariablesOfDifferentTypesInDeclaration";
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.multipleTypedDeclarationProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MultiplyTypedDeclarationVisitor();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new NormalizeDeclarationFix();
  }

  private static class MultiplyTypedDeclarationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitDeclarationStatement(
      PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      PsiElement[] elements = statement.getDeclaredElements();
      if (elements.length > 1) {
        PsiType baseType = ((PsiVariable)elements[0]).getType();
        boolean hasMultipleTypes = false;
        for (int i = 1; i < elements.length; i++) {
          PsiLocalVariable var = (PsiLocalVariable)elements[i];
          PsiType variableType = var.getType();
          if (!variableType.equals(baseType)) {
            hasMultipleTypes = true;
          }
        }
        if (hasMultipleTypes) {
          for (int i = 1; i < elements.length; i++) {
            PsiLocalVariable var =
              (PsiLocalVariable)elements[i];
            registerVariableError(var);
          }
        }
      }
    }

    @Override
    public void visitField(@Nonnull PsiField field) {
      super.visitField(field);
      if (!childrenContainTypeElement(field)) {
        return;
      }
      List<PsiField> fields = getSiblingFields(field);
      if (fields.size() > 1) {
        PsiField firstField = fields.get(0);
        PsiType baseType = firstField.getType();
        boolean hasMultipleTypes = false;
        for (int i = 1; i < fields.size(); i++) {
          PsiField variable = fields.get(i);
          PsiType variableType = variable.getType();
          if (!variableType.equals(baseType)) {
            hasMultipleTypes = true;
          }
        }
        if (hasMultipleTypes) {
          for (int i = 1; i < fields.size(); i++) {
            PsiField var = fields.get(i);
            registerVariableError(var);
          }
        }
      }
    }

    public static List<PsiField> getSiblingFields(PsiField field) {
      List<PsiField> out = new ArrayList<PsiField>(5);
      out.add(field);
      PsiField nextField =
        PsiTreeUtil.getNextSiblingOfType(field,
                                         PsiField.class);
      if (nextField != null) {
        PsiTypeElement nextTypeElement = nextField.getTypeElement();
        while (nextTypeElement != null &&
               nextTypeElement.equals(field.getTypeElement())) {
          out.add(nextField);
          nextField =
            PsiTreeUtil.getNextSiblingOfType(nextField,
                                             PsiField.class);
          if (nextField == null) {
            break;
          }
          nextTypeElement = nextField.getTypeElement();
        }
      }
      return out;
    }

    public static boolean childrenContainTypeElement(PsiElement field) {
      PsiElement[] children = field.getChildren();
      for (PsiElement aChildren : children) {
        if (aChildren instanceof PsiTypeElement) {
          return true;
        }
      }
      return false;
    }
  }
}