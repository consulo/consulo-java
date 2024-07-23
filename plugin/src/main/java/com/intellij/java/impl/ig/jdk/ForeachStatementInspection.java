/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.jdk;

import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ForeachStatementInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.extendedForStatementDisplayName().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.extendedForStatementProblemDescriptor().get();
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ForEachFix();
  }

  private static class ForEachFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.extendedForStatementReplaceQuickfix().get();
    }

    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiForeachStatement statement = (PsiForeachStatement)element.getParent();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      assert statement != null;
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) {
        return;
      }
      @NonNls final StringBuilder newStatement = new StringBuilder();
      final PsiParameter iterationParameter = statement.getIterationParameter();
      final JavaCodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
      if (iteratedValue.getType() instanceof PsiArrayType) {
        final PsiType type = iterationParameter.getType();
        final String index = codeStyleManager.suggestUniqueVariableName("i", statement, true);
        newStatement.append("for(int ").append(index).append(" = 0;");
        newStatement.append(index).append('<').append(iteratedValue.getText()).append(".length;");
        newStatement.append(index).append("++)").append("{ ");
        if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
          newStatement.append("final ");
        }
        newStatement.append(type.getCanonicalText()).append(' ').append(iterationParameter.getName());
        newStatement.append(" = ").append(iteratedValue.getText()).append('[').append(index).append("];");
      }
      else {
        @NonNls final StringBuilder methodCall = new StringBuilder();
        if (ParenthesesUtils.getPrecedence(iteratedValue) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
          methodCall.append('(').append(iteratedValue.getText()).append(')');
        }
        else {
          methodCall.append(iteratedValue.getText());
        }
        methodCall.append(".iterator()");
        final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        final PsiExpression iteratorCall = factory.createExpressionFromText(methodCall.toString(), iteratedValue);
        final PsiType variableType = GenericsUtil.getVariableTypeByExpressionType(iteratorCall.getType());
        if (variableType == null) {
          return;
        }
        final PsiType parameterType = iterationParameter.getType();
        final String typeText = parameterType.getCanonicalText();
        newStatement.append("for(").append(variableType.getCanonicalText()).append(' ');
        final String iterator = codeStyleManager.suggestUniqueVariableName("iterator", statement, true);
        newStatement.append(iterator).append("=").append(iteratorCall.getText()).append(';');
        newStatement.append(iterator).append(".hasNext();){");
        if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
          newStatement.append("final ");
        }
        newStatement.append(typeText).append(' ').append(iterationParameter.getName()).append(" = ").append(iterator).append(".next();");
      }
      final PsiStatement body = statement.getBody();
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
        final PsiCodeBlock block = blockStatement.getCodeBlock();
        final PsiElement[] children = block.getChildren();
        for (int i = 1; i < children.length - 1; i++) {
          //skip the braces
          newStatement.append(children[i].getText());
        }
      }
      else {
        final String bodyText;
        if (body == null) {
          bodyText = "";
        }
        else {
          bodyText = body.getText();
        }
        newStatement.append(bodyText);
      }
      newStatement.append('}');
      replaceStatementAndShortenClassNames(statement, newStatement.toString());
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ForeachStatementVisitor();
  }

  private static class ForeachStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForeachStatement(@Nonnull PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null || !InheritanceUtil.isInheritor(iteratedValue.getType(), JavaClassNames.JAVA_LANG_ITERABLE)) {
        return;
      }
      registerStatementError(statement);
    }
  }
}