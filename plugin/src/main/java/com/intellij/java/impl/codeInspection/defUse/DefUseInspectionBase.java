/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.defUse;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.analysis.impl.psi.controlFlow.DefUseUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.ui.ex.awt.JBUI;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class DefUseInspectionBase extends BaseJavaBatchLocalInspectionTool {
  public boolean REPORT_PREFIX_EXPRESSIONS = false;
  public boolean REPORT_POSTFIX_EXPRESSIONS = true;
  public boolean REPORT_REDUNDANT_INITIALIZER = true;

  @NonNls
  public static final String SHORT_NAME = "UnusedAssignment";

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(@Nonnull final ProblemsHolder holder,
                                            final boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new JavaElementVisitor() {
      @Override
      @RequiredReadAction
      public void visitMethod(PsiMethod method) {
        checkCodeBlock(method.getBody(), holder, isOnTheFly);
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        checkCodeBlock(initializer.getBody(), holder, isOnTheFly);
      }
    };
  }

  @RequiredReadAction
  private void checkCodeBlock(
    final PsiCodeBlock body,
    final ProblemsHolder holder,
    final boolean isOnTheFly
  ) {
    if (body == null) return;
    final Set<PsiVariable> usedVariables = new HashSet<>();
    List<DefUseUtil.Info> unusedDefs = DefUseUtil.getUnusedDefs(body, usedVariables);

    if (unusedDefs != null && !unusedDefs.isEmpty()) {
      Collections.sort(
        unusedDefs,
        (o1, o2) -> {
          int offset1 = o1.getContext().getTextOffset();
          int offset2 = o2.getContext().getTextOffset();

          if (offset1 == offset2) return 0;
          if (offset1 < offset2) return -1;

          return 1;
        }
      );

      for (DefUseUtil.Info info : unusedDefs) {
        PsiElement context = info.getContext();
        PsiVariable psiVariable = info.getVariable();

        if (context instanceof PsiDeclarationStatement || context instanceof PsiResourceVariable) {
          if (!info.isRead()) {
            if (!isOnTheFly) {
              holder.registerProblem(
                psiVariable.getNameIdentifier(),
                InspectionLocalize.inspectionUnusedAssignmentProblemDescriptor1("<code>#ref</code> #loc").get(),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL
              );
            }
          } else {
            if (REPORT_REDUNDANT_INITIALIZER) {
              holder.registerProblem(
                psiVariable.getInitializer(),
                InspectionLocalize.inspectionUnusedAssignmentProblemDescriptor2(
                  "<code>" + psiVariable.getName() + "</code>",
                  "<code>#ref</code> #loc"
                ).get(),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                createRemoveInitializerFix()
              );
            }
          }
        } else if (context instanceof PsiAssignmentExpression assignment) {
          holder.registerProblem(
            assignment.getLExpression(),
            InspectionLocalize.inspectionUnusedAssignmentProblemDescriptor3(
              assignment.getRExpression().getText(),
              "<code>#ref</code>" + " #loc"
            ).get(),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL
          );
        } else {
          if (context instanceof PsiPrefixExpression && REPORT_PREFIX_EXPRESSIONS ||
              context instanceof PsiPostfixExpression && REPORT_POSTFIX_EXPRESSIONS) {
            holder.registerProblem(
              context,
              InspectionLocalize.inspectionUnusedAssignmentProblemDescriptor4("<code>#ref</code> #loc").get()
            );
          }
        }
      }
    }

    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
      }

      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        if (!usedVariables.contains(variable) && variable.getInitializer() == null && !isOnTheFly) {
          holder.registerProblem(
            variable.getNameIdentifier(),
            InspectionLocalize.inspectionUnusedAssignmentProblemDescriptor5("<code>#ref</code> #loc").get(),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL
          );
        }
      }

      @Override
      @RequiredReadAction
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression lExpression = expression.getLExpression();
        PsiExpression rExpression = expression.getRExpression();

        if (lExpression instanceof PsiReferenceExpression lRef && rExpression instanceof PsiReferenceExpression rRef) {
          if (lRef.resolve() != rRef.resolve()) return;
          PsiExpression lQualifier = lRef.getQualifierExpression();
          PsiExpression rQualifier = rRef.getQualifierExpression();

          if ((lQualifier == null && rQualifier == null ||
              lQualifier instanceof PsiThisExpression && rQualifier instanceof PsiThisExpression ||
              lQualifier instanceof PsiThisExpression && rQualifier == null ||
              lQualifier == null && rQualifier instanceof PsiThisExpression) && !isOnTheFly) {
            holder.registerProblem(
              expression,
              InspectionLocalize.inspectionUnusedAssignmentProblemDescriptor6("<code>#ref</code>").get()
            );
          }
        }
      }
    });
  }

  protected LocalQuickFix createRemoveInitializerFix() {
    return null;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myReportPrefix;
    private final JCheckBox myReportPostfix;
    private final JCheckBox myReportInitializer;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myReportInitializer = new JCheckBox(InspectionLocalize.inspectionUnusedAssignmentOption2().get());
      myReportInitializer.setSelected(REPORT_REDUNDANT_INITIALIZER);
      myReportInitializer.getModel().addChangeListener(e -> REPORT_REDUNDANT_INITIALIZER = myReportInitializer.isSelected());
      gc.insets = JBUI.insetsBottom(15);
      gc.gridy = 0;
      add(myReportInitializer, gc);

      myReportPrefix = new JCheckBox(InspectionLocalize.inspectionUnusedAssignmentOption().get());
      myReportPrefix.setSelected(REPORT_PREFIX_EXPRESSIONS);
      myReportPrefix.getModel().addChangeListener(e -> REPORT_PREFIX_EXPRESSIONS = myReportPrefix.isSelected());
      gc.insets = JBUI.emptyInsets();
      gc.gridy++;
      add(myReportPrefix, gc);

      myReportPostfix = new JCheckBox(InspectionLocalize.inspectionUnusedAssignmentOption1().get());
      myReportPostfix.setSelected(REPORT_POSTFIX_EXPRESSIONS);
      myReportPostfix.getModel().addChangeListener(e-> REPORT_POSTFIX_EXPRESSIONS = myReportPostfix.isSelected());

      gc.weighty = 1;
      gc.gridy++;
      add(myReportPostfix, gc);
    }
  }


  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionLocalize.inspectionUnusedAssignmentDisplayName().get();
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesProbableBugs().get();
  }

  @Override
  @Nonnull
  public String getShortName() {
    return SHORT_NAME;
  }
}