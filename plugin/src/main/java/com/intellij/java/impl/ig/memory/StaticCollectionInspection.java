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
package com.intellij.java.impl.ig.memory;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class StaticCollectionInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreWeakCollections = false;

  @jakarta.annotation.Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "static.collection.display.name");
  }

  @jakarta.annotation.Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "static.collection.problem.descriptor");
  }

  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "static.collection.ignore.option"),
                                          this, "m_ignoreWeakCollections");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new StaticCollectionVisitor();
  }

  private class StaticCollectionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiType type = field.getType();
      if (!CollectionUtils.isCollectionClassOrInterface(type)) {
        return;
      }
      if (!m_ignoreWeakCollections ||
          CollectionUtils.isWeakCollectionClass(type)) {
        return;
      }
      registerFieldError(field);
    }
  }
}