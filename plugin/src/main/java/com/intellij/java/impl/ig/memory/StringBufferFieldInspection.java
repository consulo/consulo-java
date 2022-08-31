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
package com.intellij.java.impl.ig.memory;

import consulo.java.language.module.util.JavaClassNames;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;

import javax.annotation.Nonnull;

public class StringBufferFieldInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "stringbuffer.field.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final String typeName = type.getPresentableText();
    return InspectionGadgetsBundle.message(
      "stringbuffer.field.problem.descriptor", typeName);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferFieldVisitor();
  }

  private static class StringBufferFieldVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      super.visitField(field);
      final PsiType type = field.getType();
      if (!type.equalsToText(JavaClassNames.JAVA_LANG_STRING_BUFFER) &&
          !type.equalsToText(JavaClassNames.JAVA_LANG_STRING_BUILDER)) {
        return;
      }
      registerFieldError(field, type);
    }
  }
}