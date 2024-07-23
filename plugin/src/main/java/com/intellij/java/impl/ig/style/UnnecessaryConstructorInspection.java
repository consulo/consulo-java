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
package com.intellij.java.impl.ig.style;

import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.*;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

import javax.swing.JComponent;

@ExtensionImpl
public class UnnecessaryConstructorInspection extends BaseInspection {

  @NonNls
  private static final String SUPER_CALL_TEXT = PsiKeyword.SUPER + "();";

  @SuppressWarnings("PublicField")
  public boolean ignoreAnnotations = false;

  @Override
  @Nonnull
  public String getID() {
    return "RedundantNoArgConstructor";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unnecessaryConstructorDisplayName().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.unnecessaryConstructorAnnotationOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreAnnotations");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.unnecessaryConstructorProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryConstructorVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryConstructorFix();
  }

  private static class UnnecessaryConstructorFix extends InspectionGadgetsFix {
    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.unnecessaryConstructorRemoveQuickfix().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement nameIdentifier = descriptor.getPsiElement();
      final PsiElement constructor = nameIdentifier.getParent();
      assert constructor != null;
      deleteElement(constructor);
    }
  }

  private class UnnecessaryConstructorVisitor extends BaseInspectionVisitor {
    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length != 1) {
        return;
      }
      final PsiMethod constructor = constructors[0];
      if (!constructor.hasModifierProperty(PsiModifier.PRIVATE) &&
          aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (!constructor.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
          aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        return;
      }
      if (!constructor.hasModifierProperty(PsiModifier.PROTECTED) &&
          aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      if (!constructor.hasModifierProperty(PsiModifier.PUBLIC) &&
          aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiParameterList parameterList =
        constructor.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      if (ignoreAnnotations) {
        final PsiModifierList modifierList =
          constructor.getModifierList();
        final PsiAnnotation[] annotations =
          modifierList.getAnnotations();
        if (annotations.length > 0) {
          return;
        }
      }
      final PsiReferenceList throwsList = constructor.getThrowsList();
      final PsiJavaCodeReferenceElement[] elements =
        throwsList.getReferenceElements();
      if (elements.length != 0) {
        return;
      }
      final PsiCodeBlock body = constructor.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        registerMethodError(constructor);
      }
      else if (statements.length == 1) {
        final PsiStatement statement = statements[0];
        if (SUPER_CALL_TEXT.equals(statement.getText())) {
          registerMethodError(constructor);
        }
      }
    }
  }
}