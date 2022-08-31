/*
 * User: anna
 * Date: 22-Jul-2008
 */
package com.intellij.refactoring;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.annotation.Nonnull;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.impl.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import consulo.codeInsight.TargetElementUtil;
import consulo.codeInsight.TargetElementUtilEx;

public abstract class FindMethodDuplicatesBaseTest extends LightCodeInsightTestCase {
  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected void doTest() throws Exception {
    doTest(true);
  }

  protected void doTest(final boolean shouldSucceed) throws Exception {
    final String filePath = getTestFilePath();
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED));
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMember);
    final PsiMember psiMethod = (PsiMember)targetElement;

    try {
      MethodDuplicatesHandler.invokeOnScope(getProject(), psiMethod, new AnalysisScope(getFile()));
    }
    catch (RuntimeException e) {
      if (shouldSucceed) {
        fail("duplicates were not found");
      }
      return;
    }
    UIUtil.dispatchAllInvocationEvents();
    if (shouldSucceed) {
      checkResultByFile(filePath + ".after");
    }
    else {
      fail("duplicates found");
    }
  }

  protected abstract String getTestFilePath();
}