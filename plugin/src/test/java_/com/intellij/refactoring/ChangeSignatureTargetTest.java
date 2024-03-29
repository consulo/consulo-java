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
 * Date: 25-Nov-2009
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.impl.refactoring.changeSignature.JavaChangeSignatureHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

public abstract class ChangeSignatureTargetTest extends LightCodeInsightTestCase {
  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInMethodParameters() throws Exception {
    doTest("foo");
  }

  public void testInMethodArguments() throws Exception {
    doTest("foo");
  }

  public void testInClassTypeParameters() throws Exception {
    doTest("A1");
  }

  public void testInTypeArguments() throws Exception {
    doTest("A1");
  }

  private void doTest(String expectedMemberName) throws Exception {
    String basePath = "/refactoring/changeSignatureTarget/" + getTestName(true);
    @NonNls final String filePath = basePath + ".java";
    configureByFile(filePath);
    final PsiElement member = new JavaChangeSignatureHandler().findTargetMember(getFile(), getEditor());
    assertNotNull(member);
    assertEquals(expectedMemberName, ((PsiMember)member).getName());
  }
}
