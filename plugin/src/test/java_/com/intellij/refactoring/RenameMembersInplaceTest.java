/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.intellij.JavaTestUtil;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import consulo.language.editor.TargetElementUtil;
import jakarta.annotation.Nonnull;

/**
 * User: anna
 * Date: 12/4/11
 */
public abstract class RenameMembersInplaceTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/renameInplace/";

  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInnerClass() throws Exception {
    doTestInplaceRename("NEW_NAME");
  }
  
  public void testConstructor() throws Exception {
    doTestInplaceRename("Bar");
  }

  public void testSuperMethod() throws Exception {
    doTestInplaceRename("xxx");
  }
  
  public void testSuperMethodAnonymousInheritor() throws Exception {
    doTestInplaceRename("xxx");
  }

  public void testMultipleConstructors() throws Exception {
    doTestInplaceRename("Bar");
  }

  public void testClassWithMultipleConstructors() throws Exception {
    doTestInplaceRename("Bar");
  }
  
  public void testMethodWithJavadocRef() throws Exception {
    doTestInplaceRename("bar");
  }
  
  public void testEnumConstructor() throws Exception {
    doTestInplaceRename("Bar");
  }

  public void testMethodWithMethodRef() throws Exception {
    doTestInplaceRename("bar");
  }

  public void testConflictingMethodName() throws Exception {
    try {
      doTestInplaceRename("bar");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method bar() is already defined in the class <b><code>Foo</code></b>.", e.getMessage());
      checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
      return;
    }
    fail("Conflict was not detected");
  }

  private void doTestInplaceRename(final String newName) throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");

    final PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.getAllAccepted());
    assertNotNull(element);

    CodeInsightTestUtil.doInlineRename(new MemberInplaceRenameHandler(), newName, getEditor(), element);

    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
