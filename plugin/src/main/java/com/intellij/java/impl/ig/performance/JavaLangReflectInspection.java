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
package com.intellij.java.impl.ig.performance;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiVariable;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class JavaLangReflectInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "java.lang.reflect.display.name");
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "java.lang.reflect.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new JavaLangReflectVisitor();
  }

  private static class JavaLangReflectVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@Nonnull PsiVariable variable) {
      super.visitVariable(variable);
      final PsiType type = variable.getType();
      final PsiType componentType = type.getDeepComponentType();
      if (!(componentType instanceof PsiClassType)) {
        return;
      }
      final String className = ((PsiClassType)componentType).getClassName();
      @NonNls final String javaLangReflect = "java.lang.reflect.";
      if (!className.startsWith(javaLangReflect)) {
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) {
        return;
      }
      registerError(typeElement);
    }
  }
}