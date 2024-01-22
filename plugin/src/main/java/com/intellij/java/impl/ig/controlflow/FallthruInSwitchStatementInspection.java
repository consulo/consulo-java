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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.regex.Pattern;

@ExtensionImpl
public class FallthruInSwitchStatementInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("fallthru.in.switch.statement.display.name");
  }

  @jakarta.annotation.Nonnull
  public String getID() {
    return "fallthrough";
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("fallthru.in.switch.statement.problem.descriptor");
  }

  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new FallthruInSwitchStatementFix();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new FallthroughInSwitchStatementVisitor();
  }

  private static class FallthruInSwitchStatementFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message("fallthru.in.switch.statement.quickfix");
    }

    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)descriptor.getPsiElement();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiStatement breakStatement = factory.createStatementFromText("break;", labelStatement);
      final PsiElement parent = labelStatement.getParent();
      parent.addBefore(breakStatement, labelStatement);
    }
  }

  private static class FallthroughInSwitchStatementVisitor extends BaseInspectionVisitor {

    private static final Pattern commentPattern = Pattern.compile("(?i)falls?\\s*thro?u");

    @Override
    public void visitSwitchStatement(@Nonnull PsiSwitchStatement switchStatement) {
      super.visitSwitchStatement(switchStatement);
      final PsiCodeBlock body = switchStatement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      for (int i = 1; i < statements.length; i++) {
        final PsiStatement statement = statements[i];
        if (!(statement instanceof PsiSwitchLabelStatement)) {
          continue;
        }
        final PsiElement previousSibling = PsiTreeUtil.skipSiblingsBackward(statement, PsiWhiteSpace.class);
        if (previousSibling instanceof PsiComment) {
          final PsiComment comment = (PsiComment)previousSibling;
          final String commentText = comment.getText();
          if (commentPattern.matcher(commentText).find()) {
            continue;
          }
        }
        final PsiStatement previousStatement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
        if (previousStatement instanceof PsiSwitchLabelStatement) {
          // don't warn if there are no regular statements after the switch label
          continue;
        }
        if (ControlFlowUtils.statementMayCompleteNormally(previousStatement)) {
          registerError(statement);
        }
      }
    }
  }
}