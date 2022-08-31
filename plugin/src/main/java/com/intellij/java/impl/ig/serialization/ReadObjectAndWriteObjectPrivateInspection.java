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
package com.intellij.java.impl.ig.serialization;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.ChangeModifierFix;
import com.intellij.java.impl.ig.psiutils.SerializationUtils;

public class ReadObjectAndWriteObjectPrivateInspection
  extends BaseInspection {

  @Nonnull
  public String getID() {
    return "NonPrivateSerializationMethod";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "readwriteobject.private.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "readwriteobject.private.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ReadObjectWriteObjectPrivateVisitor();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ChangeModifierFix(PsiModifier.PRIVATE);
  }

  private static class ReadObjectWriteObjectPrivateVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      // no call to super, so it doesn't drill down
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (!SerializationUtils.isReadObject(method) &&
          !SerializationUtils.isWriteObject(method)) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      registerMethodError(method);
    }
  }
}