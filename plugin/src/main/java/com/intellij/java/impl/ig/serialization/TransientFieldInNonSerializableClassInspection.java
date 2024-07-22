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

import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class TransientFieldInNonSerializableClassInspection
  extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.transientFieldInNonSerializableClassDisplayName().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return InspectionGadgetsLocalize.transientFieldInNonSerializableClassProblemDescriptor(field.getName()).get();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new TransientFieldInNonSerializableClassFix();
  }


  private static class TransientFieldInNonSerializableClassFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.transientFieldInNonSerializableClassRemoveQuickfix().get();
    }

    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
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