/*
 * Copyright 2007-2012 Bas Leijdekkers
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class SynchronizedOnLiteralObjectInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean warnOnAllPossiblyLiterals = false;

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.synchronizedOnLiteralObjectName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    String typeText = ((PsiType)infos[0]).getPresentableText();
    int message = (Integer)infos[1];
    switch (message) {
      case 1:
        return InspectionGadgetsLocalize.synchronizedOnLiteralObjectProblemDescriptor(typeText).get();
      case 2:
        return InspectionGadgetsLocalize.synchronizedOnDirectLiteralObjectProblemDescriptor(typeText).get();
      case 3:
        return InspectionGadgetsLocalize.synchronizedOnPossiblyLiteralObjectProblemDescriptor(typeText).get();
      default:
        throw new AssertionError();
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.synchronizedOnLiteralObjectWarnOnAllOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "warnOnAllPossiblyLiterals");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizeOnLiteralVisitor();
  }

  private class SynchronizeOnLiteralVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(@Nonnull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      PsiExpression lockExpression = statement.getLockExpression();
      if (lockExpression == null) {
        return;
      }
      PsiType type = lockExpression.getType();
      if (type == null) {
        return;
      }
      if (!type.equalsToText(CommonClassNames.JAVA_LANG_STRING) &&
          !type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN) &&
          !type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER)) {
        PsiClassType javaLangNumberType = TypeUtils.getType(CommonClassNames.JAVA_LANG_NUMBER, statement);
        if (!javaLangNumberType.isAssignableFrom(type)) {
          return;
        }
      }
      if (!(lockExpression instanceof PsiReferenceExpression)) {
        if (ExpressionUtils.isLiteral(lockExpression)) {
          registerError(lockExpression, type, Integer.valueOf(2));
        }
        else if (warnOnAllPossiblyLiterals) {
          registerError(lockExpression, type, Integer.valueOf(3));
        }
        return;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lockExpression;
      PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        if (warnOnAllPossiblyLiterals) {
          registerError(lockExpression, type, Integer.valueOf(3));
        }
        return;
      }
      PsiVariable variable = (PsiVariable)target;
      PsiExpression initializer = variable.getInitializer();
      if (!ExpressionUtils.isLiteral(initializer)) {
        if (warnOnAllPossiblyLiterals) {
          registerError(lockExpression, type, Integer.valueOf(3));
        }
        return;
      }
      registerError(lockExpression, type, Integer.valueOf(1));
    }
  }
}