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

import com.intellij.java.impl.ig.fixes.EqualityToEqualsFix;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressManager;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.Collection;

@ExtensionImpl
public class ObjectEqualityInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreEnums = true;

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreClassObjects = false;

  /**
   * @noinspection PublicField
   */
  public boolean m_ignorePrivateConstructors = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.objectComparisonDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.objectComparisonProblemDescription().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsLocalize.objectComparisonEnumeratedIgnoreOption().get(), "m_ignoreEnums");
    optionsPanel.addCheckbox(InspectionGadgetsLocalize.objectComparisonKlassIgnoreOption().get(), "m_ignoreClassObjects");
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.objectEqualityIgnoreBetweenObjectsOfATypeWithOnlyPrivateConstructorsOption().get(),
      "m_ignorePrivateConstructors"
    );
    return optionsPanel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectEqualityVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new EqualityToEqualsFix();
  }

  private class ObjectEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@Nonnull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      if (!isObjectType(rhs)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      if (!isObjectType(lhs)) {
        return;
      }
      if (m_ignoreEnums && (isEnumType(rhs) || isEnumType(lhs))) {
        return;
      }
      if (m_ignoreClassObjects && (isClass(rhs) || isClass(lhs))) {
        return;
      }
      if (m_ignorePrivateConstructors && (typeHasPrivateConstructor(lhs) || typeHasPrivateConstructor(rhs))) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (method != null && MethodUtils.isEquals(method)) {
        return;
      }
      final PsiJavaToken sign = expression.getOperationSign();
      registerError(sign);
    }

    private boolean typeHasPrivateConstructor(@Nullable PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass != null && aClass.isInterface()) {
        return implementersHaveOnlyPrivateConstructors(aClass);
      }
      else {
        return hasOnlyPrivateConstructors(aClass);
      }
    }

    private boolean implementersHaveOnlyPrivateConstructors(final PsiClass aClass) {
      final GlobalSearchScope scope = GlobalSearchScope.allScope(aClass.getProject());
      final PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = new PsiElementProcessor.CollectElementsWithLimit(6);
      final ProgressManager progressManager = ProgressManager.getInstance();
      progressManager.runProcess(new Runnable() {
        public void run() {
          ClassInheritorsSearch.search(aClass, scope, true, true).forEach(new PsiElementProcessorAdapter<PsiClass>(processor));
        }
      }, null);
      if (processor.isOverflow()) {
        return false;
      }
      final Collection<PsiClass> implementers = processor.getCollection();
      for (PsiClass implementer : implementers) {
        if (!implementer.isInterface() && !implementer.hasModifierProperty(PsiModifier.ABSTRACT)) {
          if (!hasOnlyPrivateConstructors(implementer)) {
            return false;
          }
        }
      }
      return true;
    }

    private boolean hasOnlyPrivateConstructors(PsiClass aClass) {
      if (aClass == null) {
        return false;
      }
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length == 0) {
        return false;
      }
      for (PsiMethod constructor : constructors) {
        if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          return false;
        }
      }
      return true;
    }

    private boolean isClass(@Nullable PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PsiClassObjectAccessExpression) {
        return true;
      }
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClassType rawType = classType.rawType();
      return rawType.equalsToText(CommonClassNames.JAVA_LANG_CLASS);
    }

    private boolean isEnumType(@Nullable PsiExpression expression) {
      return expression != null && TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_ENUM);
    }

    private boolean isObjectType(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      return type != null &&
             !(type instanceof PsiArrayType) &&
             !(type instanceof PsiPrimitiveType) &&
             !TypeUtils.isJavaLangString(type) &&
             !TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_NUMBER);
    }
  }
}
