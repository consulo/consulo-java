/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 16-May-2007
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.java.impl.codeInspection.varScopeCanBeNarrowed.ParameterCanBeLocalInspection;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

public abstract class ConvertParameterToLocalVariableTest extends LightQuickFixTestCase {
  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected void doAction(final String text, final boolean actionShouldBeAvailable, final String testFullPath, final String testName)
    throws Exception {

    final LocalQuickFix fix = new ParameterCanBeLocalInspection.ConvertParameterToLocalQuickFix();
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiElement psiElement = getFile().findElementAt(offset);
    assert psiElement != null;
    final InspectionManager manager = InspectionManager.getInstance(getProject());
    final ProblemDescriptor descriptor = manager.createProblemDescriptor(psiElement, "", fix, ProblemHighlightType.LIKE_UNUSED_SYMBOL, true);
    fix.applyFix(getProject(), descriptor);
    final String expectedFilePath = getBasePath() + "/after" + testName;
    checkResultByFile("In file :" + expectedFilePath, expectedFilePath, false);
  }


  @Override
  @NonNls
  protected String getBasePath() {
    return "/quickFix/ConvertParameterToLocalVariable";
  }
}
