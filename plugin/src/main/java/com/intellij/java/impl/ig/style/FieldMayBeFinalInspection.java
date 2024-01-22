/*
 * Copyright 2008-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.analysis.impl.codeInspection.canBeFinal.CanBeFinalHandler;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.MakeFieldFinalFix;
import com.intellij.java.impl.ig.psiutils.FinalUtils;
import consulo.annotation.component.ExtensionImpl;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class FieldMayBeFinalInspection extends BaseInspection {

  @Override
  @jakarta.annotation.Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "field.may.be.final.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "field.may.be.final.problem.descriptor");
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return MakeFieldFinalFix.buildFixUnconditional((PsiField)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FieldMayBeFinalVisitor();
  }

  private static class FieldMayBeFinalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      if (field.hasModifierProperty(PsiModifier.FINAL) ||
          !field.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (!CanBeFinalHandler.allowToBeFinal(field)) return;
      if (!FinalUtils.canBeFinal(field)) {
        return;
      }
      registerVariableError(field, field);
    }
  }
}
