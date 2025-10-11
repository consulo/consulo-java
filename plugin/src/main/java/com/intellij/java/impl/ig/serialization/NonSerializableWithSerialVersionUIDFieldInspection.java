/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.fixes.MakeSerializableFix;
import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.siyeh.HardcodedMethodConstants;
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
public class NonSerializableWithSerialVersionUIDFieldInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "NonSerializableClassWithSerialVersionUID";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.nonSerializableWithSerialversionuidDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (aClass.isAnnotationType()) {
      return InspectionGadgetsLocalize.nonSerializableInterfaceWithSerialversionuidProblemDescriptor().get();
    }
    else if (aClass.isInterface()) {
      return InspectionGadgetsLocalize.nonSerializableInterfaceWithSerialversionuidProblemDescriptor().get();
    }
    else if (aClass instanceof PsiAnonymousClass) {
      return InspectionGadgetsLocalize.nonSerializableAnonymousWithSerialversionuidProblemDescriptor().get();
    }
    else {
      return InspectionGadgetsLocalize.nonSerializableClassWithSerialversionuidProblemDescriptor().get();
    }
  }

  @Override
  @Nonnull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (aClass.isAnnotationType() || aClass.isInterface() ||
        aClass instanceof PsiAnonymousClass) {
      return new InspectionGadgetsFix[]{new RemoveSerialVersionUIDFix()};
    }
    return new InspectionGadgetsFix[]{new MakeSerializableFix(),
      new RemoveSerialVersionUIDFix()};
  }

  private static class RemoveSerialVersionUIDFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.nonSerializableWithSerialversionuidRemoveQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement nameElement = descriptor.getPsiElement();
      final PsiClass aClass = (PsiClass)nameElement.getParent();
      final PsiField field = aClass.findFieldByName(
        HardcodedMethodConstants.SERIAL_VERSION_UID, false);
      if (field == null) {
        return;
      }
      field.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonSerializableWithSerialVersionUIDVisitor();
  }

  private static class NonSerializableWithSerialVersionUIDVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      final PsiField field = aClass.findFieldByName(
        HardcodedMethodConstants.SERIAL_VERSION_UID, false);
      if (field == null) {
        return;
      }
      if (SerializationUtils.isSerializable(aClass)) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}