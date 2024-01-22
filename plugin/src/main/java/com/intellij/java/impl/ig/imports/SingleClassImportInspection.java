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
package com.intellij.java.impl.ig.imports;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiImportList;
import com.intellij.java.language.psi.PsiImportStatement;
import com.intellij.java.language.psi.PsiJavaFile;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class SingleClassImportInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "single.class.import.display.name");
  }

  @jakarta.annotation.Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "single.class.import.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new PackageImportVisitor();
  }

  private static class PackageImportVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@jakarta.annotation.Nonnull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (!(aClass.getParent() instanceof PsiJavaFile)) {
        return;
      }
     /* if (JspPsiUtil.isInJspFile(aClass.getContainingFile())) {
        return;
      }     */
      final PsiJavaFile file = (PsiJavaFile)aClass.getParent();
      if (file == null) {
        return;
      }
      if (!file.getClasses()[0].equals(aClass)) {
        return;
      }
      final PsiImportList importList = file.getImportList();
      if (importList == null) {
        return;
      }
      final PsiImportStatement[] importStatements =
        importList.getImportStatements();
      for (final PsiImportStatement importStatement : importStatements) {
        if (!importStatement.isOnDemand()) {
          registerError(importStatement);
        }
      }
    }
  }
}