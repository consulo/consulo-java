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
  protected void doAction(String text, boolean actionShouldBeAvailable, String testFullPath, String testName)
    throws Exception {

    LocalQuickFix fix = new ParameterCanBeLocalInspection.ConvertParameterToLocalQuickFix();
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement psiElement = getFile().findElementAt(offset);
    assert psiElement != null;
    InspectionManager manager = InspectionManager.getInstance(getProject());
    ProblemDescriptor descriptor = manager.createProblemDescriptor(psiElement, "", fix, ProblemHighlightType.LIKE_UNUSED_SYMBOL, true);
    fix.applyFix(getProject(), descriptor);
    String expectedFilePath = getBasePath() + "/after" + testName;
    checkResultByFile("In file :" + expectedFilePath, expectedFilePath, false);
  }


  @Override
  @NonNls
  protected String getBasePath() {
    return "/quickFix/ConvertParameterToLocalVariable";
  }
}
