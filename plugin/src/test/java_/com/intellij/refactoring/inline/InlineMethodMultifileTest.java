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
package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.refactoring.inline.InlineMethodProcessor;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.MockInlineMethodOptions;
import com.intellij.refactoring.RefactoringTestCase;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;


public abstract class InlineMethodMultifileTest extends RefactoringTestCase {

  private String getRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineMethod/multifile/" + getTestName(true);
  }

  public void testRemoveStaticImports() throws Exception {
    doTest("Foo", "foo");
  }

  private void doTest(String className, String methodName) throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    final VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
    PsiClass aClass = myJavaFacade.findClass(className, ProjectScope.getAllScope(myProject));
    assertTrue(aClass != null);
    PsiElement element = aClass.findMethodsByName(methodName, false)[0];
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod)element;
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    assertFalse("Bad returns found", condition);

    InlineOptions options = new MockInlineMethodOptions();
    final InlineMethodProcessor processor = new InlineMethodProcessor(getProject(), method, null, myEditor, options.isInlineThisOnly());
    processor.run();

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
  }
}
