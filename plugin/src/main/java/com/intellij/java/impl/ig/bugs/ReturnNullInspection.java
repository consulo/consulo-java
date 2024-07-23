/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.analysis.impl.codeInspection.AnnotateMethodFix;
import com.intellij.java.impl.ig.DelegatingFix;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;

@ExtensionImpl
public class ReturnNullInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean m_reportObjectMethods = true;
  @SuppressWarnings({"PublicField"})
  public boolean m_reportArrayMethods = true;
  @SuppressWarnings({"PublicField"})
  public boolean m_reportCollectionMethods = true;
  @SuppressWarnings({"PublicField"})
  public boolean m_ignorePrivateMethods = false;

  @Override
  @Pattern("[a-zA-Z_0-9.-]+")
  @Nonnull
  public String getID() {
    return "ReturnOfNull";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.returnOfNullDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.returnOfNullProblemDescriptor().get();
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiElement elt = (PsiElement)infos[0];
    if (!AnnotationUtil.isAnnotatingApplicable(elt)) {
      return null;
    }
    final NullableNotNullManager manager =
      NullableNotNullManager.getInstance(elt.getProject());
    return new DelegatingFix(new AnnotateMethodFix(
      manager.getDefaultNullable(),
      ArrayUtil.toStringArray(manager.getNotNulls())));
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsLocalize.returnOfNullIgnorePrivateOption().get(), "m_ignorePrivateMethods");
    optionsPanel.addCheckbox(InspectionGadgetsLocalize.returnOfNullArraysOption().get(), "m_reportArrayMethods");
    optionsPanel.addCheckbox(InspectionGadgetsLocalize.returnOfNullCollectionsOption().get(), "m_reportCollectionMethods");
    optionsPanel.addCheckbox(InspectionGadgetsLocalize.returnOfNullObjectsOption().get(), "m_reportObjectMethods");
    return optionsPanel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReturnNullVisitor();
  }

  private class ReturnNullVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(
      @Nonnull PsiLiteralExpression value) {
      super.visitLiteralExpression(value);
      final String text = value.getText();
      if (!PsiKeyword.NULL.equals(text)) {
        return;
      }
      PsiElement parent = value.getParent();
      while (parent instanceof PsiParenthesizedExpression ||
             parent instanceof PsiConditionalExpression ||
             parent instanceof PsiTypeCastExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiReturnStatement)) {
        return;
      }
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(value, PsiMethod.class);
      if (method == null) {
        return;
      }
      if (m_ignorePrivateMethods &&
          method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      final boolean isArray = returnType.getArrayDimensions() > 0;
      final NullableNotNullManager nullableNotNullManager =
        NullableNotNullManager.getInstance(method.getProject());
      if (nullableNotNullManager.isNullable(method, false)) {
        return;
      }
      if (CollectionUtils.isCollectionClassOrInterface(returnType)) {
        if (m_reportCollectionMethods) {
          registerError(value, value);
        }
      }
      else if (isArray) {
        if (m_reportArrayMethods) {
          registerError(value, value);
        }
      }
      else {
        if (m_reportObjectMethods) {
          registerError(value, value);
        }
      }
    }
  }
}
