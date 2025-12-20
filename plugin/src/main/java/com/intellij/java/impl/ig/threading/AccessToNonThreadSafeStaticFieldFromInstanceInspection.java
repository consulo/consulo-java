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

import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.List;

@ExtensionImpl
public class AccessToNonThreadSafeStaticFieldFromInstanceInspection
  extends BaseInspection {

  @NonNls
  @SuppressWarnings({"PublicField"})
  public String nonThreadSafeTypes = "";
  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet nonThreadSafeClasses =
    new ExternalizableStringSet("java.text.SimpleDateFormat", CommonClassNames.JAVA_UTIL_CALENDAR);

  public AccessToNonThreadSafeStaticFieldFromInstanceInspection() {
    if (nonThreadSafeTypes.length() != 0) {
      nonThreadSafeClasses.clear();
      List<String> strings =
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
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.accessToNonThreadSafeStaticFieldFromInstanceDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.accessToNonThreadSafeStaticFieldFromInstanceFieldProblemDescriptor(infos[0]).get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return UiUtils.createTreeClassChooserList(
      nonThreadSafeClasses,
      InspectionGadgetsLocalize.accessToNonThreadSafeStaticFieldFromInstanceOptionTitle().get(),
      InspectionGadgetsLocalize.accessToNonThreadSafeStaticFieldFromInstanceClassChooserTitle().get()
    );
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
      PsiModifierListOwner parent =
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
        PsiSynchronizedStatement synchronizedStatement =
          PsiTreeUtil.getParentOfType(expression,
                                      PsiSynchronizedStatement.class);
        if (synchronizedStatement != null) {
          return;
        }
      }
      PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier != null) {
        return;
      }
      PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      PsiClassType classType = (PsiClassType)type;
      String className = classType.rawType().getCanonicalText();
      if (!nonThreadSafeClasses.contains(className)) {
        return;
      }
      PsiElement target = expression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      PsiField field = (PsiField)target;
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerError(expression, className);
    }
  }
}