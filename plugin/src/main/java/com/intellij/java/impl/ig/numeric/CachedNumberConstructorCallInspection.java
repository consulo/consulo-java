/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class CachedNumberConstructorCallInspection
  extends BaseInspection {

  private static final Set<String> cachedNumberTypes = new HashSet<String>();

  static {
    cachedNumberTypes.add(CommonClassNames.JAVA_LANG_LONG);
    cachedNumberTypes.add(CommonClassNames.JAVA_LANG_BYTE);
    cachedNumberTypes.add(CommonClassNames.JAVA_LANG_INTEGER);
    cachedNumberTypes.add(CommonClassNames.JAVA_LANG_SHORT);
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.cachedNumberConstructorCallDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.cachedNumberConstructorCallProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LongConstructorVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    PsiNewExpression expression = (PsiNewExpression)infos[0];
    PsiJavaCodeReferenceElement classReference =
      expression.getClassReference();
    assert classReference != null;
    String className = classReference.getText();
    return new CachedNumberConstructorCallFix(className);
  }

  private static class CachedNumberConstructorCallFix
    extends InspectionGadgetsFix {

    private final String className;

    CachedNumberConstructorCallFix(String className) {
      this.className = className;
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.cachedNumberConstructorCallQuickfix(className);
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiNewExpression expression = (PsiNewExpression)descriptor.getPsiElement();
      PsiExpressionList argList = expression.getArgumentList();
      assert argList != null;
      PsiExpression[] args = argList.getExpressions();
      PsiExpression arg = args[0];
      String text = arg.getText();
      replaceExpression(expression, className + ".valueOf(" + text + ')');
    }
  }

  private static class LongConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(
      @Nonnull PsiNewExpression expression) {
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
        return;
      }
      super.visitNewExpression(expression);
      PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      String canonicalText = type.getCanonicalText();
      if (!cachedNumberTypes.contains(canonicalText)) {
        return;
      }
      PsiClass aClass = ClassUtils.getContainingClass(expression);
      if (aClass != null &&
          cachedNumberTypes.contains(aClass.getQualifiedName())) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      PsiExpression argument = arguments[0];
      PsiType argumentType = argument.getType();
      if (argumentType == null ||
          argumentType.equalsToText(
            CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}