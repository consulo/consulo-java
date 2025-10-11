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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SetupCallsSuperSetupInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "SetUpDoesntCallSuperSetUp";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.setupCallsSuperSetupDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.setupCallsSuperSetupProblemDescriptor().get();
  }

  private static class AddSuperSetUpCall extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.setupCallsSuperSetupAddQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodName = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)methodName.getParent();
      assert method != null;
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      final PsiStatement newStatement =
        factory.createStatementFromText("super.setUp();", null);
      final CodeStyleManager styleManager =
        CodeStyleManager.getInstance(project);
      final PsiJavaToken brace = body.getLBrace();
      body.addAfter(newStatement, brace);
      styleManager.reformat(body);
    }
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AddSuperSetUpCall();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SetupCallsSuperSetupVisitor();
  }

  private static class SetupCallsSuperSetupVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      //note: no call to super;
      @NonNls final String methodName = method.getName();
      if (!"setUp".equals(methodName)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (method.getBody() == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      final PsiClass targetClass = method.getContainingClass();
      if (targetClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(targetClass,
                                       "junit.framework.TestCase")) {
        return;
      }
      final CallToSuperSetupVisitor visitor =
        new CallToSuperSetupVisitor();
      method.accept(visitor);
      if (visitor.isCallToSuperSetupFound()) {
        return;
      }
      registerMethodError(method);
    }
  }
}