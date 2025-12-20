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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ExternalizableWithSerializationMethodsInspection
  extends BaseInspection {

  @Nonnull
  public String getID() {
    return "ExternalizableClassWithSerializationMethods";
  }

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.externalizableWithSerializationMethodsDisplayName();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    boolean hasReadObject = ((Boolean)infos[0]).booleanValue();
    boolean hasWriteObject = ((Boolean)infos[1]).booleanValue();
    if (hasReadObject && hasWriteObject) {
      return InspectionGadgetsLocalize.externalizableWithSerializationMethodsProblemDescriptorBoth().get();
    }
    else if (hasWriteObject) {
      return InspectionGadgetsLocalize.externalizableWithSerializationMethodsProblemDescriptorWrite().get();
    }
    else {
      return InspectionGadgetsLocalize.externalizableWithSerializationMethodsProblemDescriptorRead().get();
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ExternalizableDefinesSerializationMethodsVisitor();
  }

  private static class ExternalizableDefinesSerializationMethodsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (!SerializationUtils.isExternalizable(aClass)) {
        return;
      }
      boolean hasReadObject =
        SerializationUtils.hasReadObject(aClass);
      boolean hasWriteObject =
        SerializationUtils.hasWriteObject(aClass);
      if (!hasWriteObject && !hasReadObject) {
        return;
      }
      registerClassError(aClass, Boolean.valueOf(hasReadObject),
                         Boolean.valueOf(hasWriteObject));
    }
  }
}