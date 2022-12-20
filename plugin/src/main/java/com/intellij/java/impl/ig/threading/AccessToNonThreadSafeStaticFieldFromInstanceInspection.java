/*
 * Copyright 2007-2011 Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.lang.StringUtil;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.intellij.java.impl.ig.ui.UiUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.JComponent;
import java.util.List;

@ExtensionImpl
public class AccessToNonThreadSafeStaticFieldFromInstanceInspection
  extends BaseInspection {

  @NonNls
  @SuppressWarnings({"PublicField"})
  public String nonThreadSafeTypes = "";
  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet nonThreadSafeClasses =
    new ExternalizableStringSet(
      "java.text.SimpleDateFormat",
      "java.util.Calendar");

  public AccessToNonThreadSafeStaticFieldFromInstanceInspection() {
    if (nonThreadSafeTypes.length() != 0) {
      nonThreadSafeClasses.clear();
      final List<String> strings =
        StringUtil.split(nonThreadSafeTypes, ",");
      for (String string : strings) {
        nonThreadSafeClasses.add(string);
      }
      nonThreadSafeTypes = "";
    }
  }

  @Nonnull
  @Override
  public String getID() {
    return "AccessToNonThreadSafeStaticField";
  }

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "access.to.non.thread.safe.static.field.from.instance.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "access.to.non.thread.safe.static.field.from.instance.field.problem.descriptor",
      infos[0]);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return UiUtils.createTreeClassChooserList(nonThreadSafeClasses,
                                              InspectionGadgetsBundle.message(
                                                "access.to.non.thread.safe.static.field.from.instance.option.title"),
                                              InspectionGadgetsBundle.message(
                                                "access.to.non.thread.safe.static.field.from.instance.class.chooser.title"));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AccessToNonThreadSafeStaticFieldFromInstanceVisitor();
  }

  class AccessToNonThreadSafeStaticFieldFromInstanceVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiModifierListOwner parent =
        PsiTreeUtil.getParentOfType(expression,
                                    PsiField.class, PsiMethod.class,
                                    PsiClassInitializer.class);
      if (parent == null) {
        return;
      }
      if (parent instanceof PsiMethod ||
          parent instanceof PsiClassInitializer) {
        if (parent.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          return;
        }
        final PsiSynchronizedStatement synchronizedStatement =
          PsiTreeUtil.getParentOfType(expression,
                                      PsiSynchronizedStatement.class);
        if (synchronizedStatement != null) {
          return;
        }
      }
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier != null) {
        return;
      }
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final String className = classType.rawType().getCanonicalText();
      if (!nonThreadSafeClasses.contains(className)) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)target;
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerError(expression, className);
    }
  }
}