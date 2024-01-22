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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import consulo.java.language.module.util.JavaClassNames;

import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class CachedNumberConstructorCallInspection
  extends BaseInspection {

  private static final Set<String> cachedNumberTypes = new HashSet<String>();

  static {
    cachedNumberTypes.add(JavaClassNames.JAVA_LANG_LONG);
    cachedNumberTypes.add(JavaClassNames.JAVA_LANG_BYTE);
    cachedNumberTypes.add(JavaClassNames.JAVA_LANG_INTEGER);
    cachedNumberTypes.add(JavaClassNames.JAVA_LANG_SHORT);
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "cached.number.constructor.call.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "cached.number.constructor.call.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LongConstructorVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiNewExpression expression = (PsiNewExpression)infos[0];
    final PsiJavaCodeReferenceElement classReference =
      expression.getClassReference();
    assert classReference != null;
    final String className = classReference.getText();
    return new CachedNumberConstructorCallFix(className);
  }

  private static class CachedNumberConstructorCallFix
    extends InspectionGadgetsFix {

    private final String className;

    CachedNumberConstructorCallFix(String className) {
      this.className = className;
    }

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "cached.number.constructor.call.quickfix", className);
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiNewExpression expression =
        (PsiNewExpression)descriptor.getPsiElement();
      final PsiExpressionList argList = expression.getArgumentList();
      assert argList != null;
      final PsiExpression[] args = argList.getExpressions();
      final PsiExpression arg = args[0];
      final String text = arg.getText();
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
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      final String canonicalText = type.getCanonicalText();
      if (!cachedNumberTypes.contains(canonicalText)) {
        return;
      }
      final PsiClass aClass = ClassUtils.getContainingClass(expression);
      if (aClass != null &&
          cachedNumberTypes.contains(aClass.getQualifiedName())) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final PsiType argumentType = argument.getType();
      if (argumentType == null ||
          argumentType.equalsToText(
            JavaClassNames.JAVA_LANG_STRING)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}