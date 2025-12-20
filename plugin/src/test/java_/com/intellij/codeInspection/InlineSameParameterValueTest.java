/*
 * User: anna
 * Date: 16-May-2007
 */
package com.intellij.codeInspection;

import jakarta.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.java.impl.codeInspection.sameParameterValue.SameParameterValueInspection;
import consulo.language.psi.PsiElement;

public abstract class InlineSameParameterValueTest extends LightQuickFixTestCase {
  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected void doAction(String text, boolean actionShouldBeAvailable, String testFullPath, String testName)
    throws Exception {
    LocalQuickFix fix = (LocalQuickFix)new SameParameterValueInspection().getQuickFix(text);
    assert fix != null;
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement psiElement = getFile().findElementAt(offset);
    assert psiElement != null;
    ProblemDescriptor descriptor = InspectionManager.getInstance(getProject())
      .createProblemDescriptor(psiElement, "", fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true);
    fix.applyFix(getProject(), descriptor);
    String expectedFilePath = getBasePath() + "/after" + testName;
    checkResultByFile("In file :" + expectedFilePath, expectedFilePath, false);
  }


  @Override
  @NonNls
  protected String getBasePath() {
    return "/quickFix/SameParameterValue";
  }
}
