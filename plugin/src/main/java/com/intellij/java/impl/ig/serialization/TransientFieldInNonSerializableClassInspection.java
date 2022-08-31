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
package com.intellij.java.impl.ig.serialization;

import javax.annotation.Nonnull;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.psiutils.SerializationUtils;

public class TransientFieldInNonSerializableClassInspection
  extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "transient.field.in.non.serializable.class.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return InspectionGadgetsBundle.message(
      "transient.field.in.non.serializable.class.problem.descriptor",
      field.getName());
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new TransientFieldInNonSerializableClassFix();
  }


  private static class TransientFieldInNonSerializableClassFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "transient.field.in.non.serializable.class.remove.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement transientModifier = descriptor.getPsiElement();
      deleteElement(transientModifier);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new TransientFieldInNonSerializableClassVisitor();
  }

  private static class TransientFieldInNonSerializableClassVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.TRANSIENT)) {
        return;
      }
      final PsiClass aClass = field.getContainingClass();
      if (SerializationUtils.isSerializable(aClass)) {
        return;
      }
      registerModifierError(PsiModifier.TRANSIENT, field, field);
    }
  }
}