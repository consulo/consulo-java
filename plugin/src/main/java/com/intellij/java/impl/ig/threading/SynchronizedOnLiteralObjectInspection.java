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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.java.language.module.util.JavaClassNames;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;

@ExtensionImpl
public class SynchronizedOnLiteralObjectInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean warnOnAllPossiblyLiterals = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("synchronized.on.literal.object.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    final String typeText = ((PsiType)infos[0]).getPresentableText();
    final int message = ((Integer)infos[1]).intValue();
    switch (message) {
      case 1:
        return InspectionGadgetsBundle.message("synchronized.on.literal.object.problem.descriptor", typeText);
      case 2:
        return InspectionGadgetsBundle.message("synchronized.on.direct.literal.object.problem.descriptor", typeText);
      case 3:
        return InspectionGadgetsBundle.message("synchronized.on.possibly.literal.object.problem.descriptor", typeText);
      default:
        throw new AssertionError();
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("synchronized.on.literal.object.warn.on.all.option"),
                                          this, "warnOnAllPossiblyLiterals");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizeOnLiteralVisitor();
  }

  private class SynchronizeOnLiteralVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(@Nonnull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiExpression lockExpression = statement.getLockExpression();
      if (lockExpression == null) {
        return;
      }
      final PsiType type = lockExpression.getType();
      if (type == null) {
        return;
      }
      if (!type.equalsToText(JavaClassNames.JAVA_LANG_STRING) &&
          !type.equalsToText(JavaClassNames.JAVA_LANG_BOOLEAN) &&
          !type.equalsToText(JavaClassNames.JAVA_LANG_CHARACTER)) {
        final PsiClassType javaLangNumberType = TypeUtils.getType(JavaClassNames.JAVA_LANG_NUMBER, statement);
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
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lockExpression;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        if (warnOnAllPossiblyLiterals) {
          registerError(lockExpression, type, Integer.valueOf(3));
        }
        return;
      }
      final PsiVariable variable = (PsiVariable)target;
      final PsiExpression initializer = variable.getInitializer();
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