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

import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class SerializableInnerClassWithNonSerializableOuterClassInspection
  extends SerializableInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.serializableInnerClassWithNonSerializableOuterClassDisplayName().get();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.serializableInnerClassWithNonSerializableOuterClassProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SerializableInnerClassWithNonSerializableOuterClassVisitor(this);
  }
}