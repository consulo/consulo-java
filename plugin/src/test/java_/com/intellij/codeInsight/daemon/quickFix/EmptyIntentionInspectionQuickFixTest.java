package com.intellij.codeInsight.daemon.quickFix;

import consulo.language.editor.internal.intention.EmptyIntentionAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.ProblemsHolder;
import com.intellij.java.impl.codeInspection.defUse.DefUseInspection;
import com.intellij.java.language.psi.JavaElementVisitor;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiLiteralExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author cdr
 */
public abstract class EmptyIntentionInspectionQuickFixTest extends LightQuickFixTestCase{
  @Override
  @NonNls
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/emptyIntention";
  }

  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new DefUseInspection(), new LocalInspectionTool() {
      @Override
      @Nls
      @Nonnull
      public String getGroupDisplayName() {
        return "MyGroup";
      }

      @Override
      @Nls
      @Nonnull
      public String getDisplayName() {
        return "My";
      }

      @Override
      @NonNls
      @Nonnull
      public String getShortName() {
        return getDisplayName();
      }

      @Override
      @Nonnull
      public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
          @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
            final String s = (String)expression.getValue();
            if (s.contains("a")) holder.registerProblem(expression, "Look ma! This String contains 'a'");
            if (s.contains("b")) holder.registerProblem(expression, "Look ma! This String contains 'b'");
          }
        };
      }
    }};
  }

  public void testX() throws Exception {
    configureByFile(getBasePath()+"/X.java");
    List<IntentionAction> emptyActions = getAvailableActions();
    for (int i = emptyActions.size()-1; i>=0; i--) {
      IntentionAction action = emptyActions.get(i);
      if (!(action instanceof EmptyIntentionAction)) emptyActions.remove(i);
    }
    assertEquals(emptyActions.toString(), 1, emptyActions.size());
  }

  public void testLowPriority() throws Exception {
    configureByFile(getBasePath() + "/LowPriority.java");
    List<IntentionAction> emptyActions = getAvailableActions();
    int i = 0;
    for(;i < emptyActions.size(); i++) {
      final IntentionAction intentionAction = emptyActions.get(i);
      if ("Make 'i' not final".equals(intentionAction.getText())) {
        break;
      }
      if (intentionAction instanceof EmptyIntentionAction) {
        fail("Low priority action prior to quick fix");
      }
    }
    assertTrue(i < emptyActions.size());
    for (; i < emptyActions.size(); i++) {
      if (emptyActions.get(i) instanceof EmptyIntentionAction) {
        return;
      }
    }
    fail("Missed inspection setting action");
  }
}
