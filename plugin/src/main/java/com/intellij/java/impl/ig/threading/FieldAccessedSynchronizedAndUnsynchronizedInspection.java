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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.impl.ig.fixes.MakeFieldFinalFix;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.Set;

@ExtensionImpl
public class FieldAccessedSynchronizedAndUnsynchronizedInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean countGettersAndSetters = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.fieldAccessedSynchronizedAndUnsynchronizedDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.fieldAccessedSynchronizedAndUnsynchronizedProblemDescriptor().get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.fieldAccessedSynchronizedAndUnsynchronizedOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "countGettersAndSetters");
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return MakeFieldFinalFix.buildFix((PsiField)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FieldAccessedSynchronizedAndUnsynchronizedVisitor();
  }

  private class FieldAccessedSynchronizedAndUnsynchronizedVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (!containsSynchronization(aClass)) {
        return;
      }
      final VariableAccessVisitor visitor =
        new VariableAccessVisitor(aClass, countGettersAndSetters);
      aClass.accept(visitor);
      final Set<PsiField> fields =
        visitor.getInappropriatelyAccessedFields();
      for (final PsiField field : fields) {
        if (field.hasModifierProperty(PsiModifier.FINAL) ||
            field.hasModifierProperty(PsiModifier.VOLATILE)) {
          continue;
        }
        final PsiClass containingClass = field.getContainingClass();
        if (aClass.equals(containingClass)) {
          registerFieldError(field, field);
        }
      }
    }

    private boolean containsSynchronization(PsiElement context) {
      final ContainsSynchronizationVisitor visitor =
        new ContainsSynchronizationVisitor();
      context.accept(visitor);
      return visitor.containsSynchronization();
    }
  }
}