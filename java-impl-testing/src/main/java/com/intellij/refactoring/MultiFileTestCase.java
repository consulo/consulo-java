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

import java.io.File;

import consulo.virtualFileSystem.LocalFileSystem;
import org.jetbrains.annotations.NonNls;
import com.intellij.codeInsight.CodeInsightTestCase;
import consulo.application.ApplicationManager;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;

/**
 * @author dsl
 */
public abstract class MultiFileTestCase extends CodeInsightTestCase {
  protected boolean myDoCompare = true;

  protected void doTest(final PerformAction performAction) throws Exception {
    doTest(performAction, getTestName(true));
  }

  protected void doTest(final PerformAction performAction, final boolean lowercaseFirstLetter) throws Exception {
    doTest(performAction, getTestName(lowercaseFirstLetter));
  }

  protected void doTest(final PerformAction performAction, final String testName) throws Exception {
    String path = getTestDataPath() + getTestRoot() + testName;

    String pathBefore = path + "/before";
    final VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, pathBefore, PlatformTestCase.myFilesToDelete, false);
    prepareProject(rootDir);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    String pathAfter = path + "/after";
    final VirtualFile rootAfter = LocalFileSystem.getInstance().findFileByPath(pathAfter.replace(File.separatorChar, '/'));

    performAction.performAction(rootDir, rootAfter);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
      }
    });

    FileDocumentManager.getInstance().saveAllDocuments();

    if (myDoCompare) {
      PlatformTestUtil.assertDirectoriesEqual(rootAfter, rootDir);
    }
  }

  protected void prepareProject(VirtualFile rootDir) {
    PsiTestUtil.addSourceContentToRoots(myModule, rootDir);
  }

  @Override
  @NonNls
  protected abstract String getTestRoot();

  protected interface PerformAction {
    void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception;
  }

  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }
}
