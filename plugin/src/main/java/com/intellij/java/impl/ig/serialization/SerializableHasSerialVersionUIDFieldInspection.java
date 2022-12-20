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
package com.intellij.java.impl.ig.serialization;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.AddSerialVersionUIDFix;
import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import consulo.annotation.component.ExtensionImpl;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class SerializableHasSerialVersionUIDFieldInspection extends SerializableInspection {

  @Pattern("[a-zA-Z_0-9.-]+")
  @Override
  @Nonnull
  public String getID() {
    return "serial";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "serializable.class.without.serialversionuid.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "serializable.class.without.serialversionuid.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AddSerialVersionUIDFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerializableHasSerialVersionUIDFieldVisitor();
  }

  private class SerializableHasSerialVersionUIDFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter || aClass instanceof PsiEnumConstantInitializer) {
        return;
      }
      if (ignoreAnonymousInnerClasses && aClass instanceof PsiAnonymousClass) {
        return;
      }
      final PsiField serialVersionUIDField = aClass.findFieldByName(HardcodedMethodConstants.SERIAL_VERSION_UID, false);
      if (serialVersionUIDField != null) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      if (SerializationUtils.hasWriteReplace(aClass)) {
        return;
      }
      if (isIgnoredSubclass(aClass)) {
        return;
      }
      registerClassError(aClass);
    }
  }
}
