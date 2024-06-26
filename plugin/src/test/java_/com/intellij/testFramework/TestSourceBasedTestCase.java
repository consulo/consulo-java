/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.testFramework;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.jetbrains.annotations.NonNls;
import consulo.application.ApplicationManager;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;

@SuppressWarnings({"HardCodedStringLiteral", "ConstantConditions", "JUnitTestCaseInProductSource"})
@NonNls public abstract class TestSourceBasedTestCase extends IdeaTestCase {
  private File myTempDirectory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempDirectory = FileUtil.createTempDirectory(getTestName(true), "test",false);
    myFilesToDelete.add(myTempDirectory);
    final File testRoot = new File(getTestDataPath(), getTestPath());
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestDirectoryName());
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    FileUtil.copyDir(currentTestRoot, new File(myTempDirectory, getTestDirectoryName()));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                                                             @Override
                                                             public void run() {
                                                               setupContentRoot();
                                                             }
                                                           });

  }

  protected String getTestDataPath() {
    return "/";
  }

  protected abstract String getTestPath();

  private File getTestContentFile() {
    return new File(myTempDirectory, getTestDirectoryName());
  }

  private void setupContentRoot() {
    PsiTestUtil.addContentRoot(myModule, getContentRoot());
    VirtualFile src = getContentRoot().findChild("src");
    if (src != null) {
      PsiTestUtil.addSourceRoot(myModule, src);
    }
  }

  protected VirtualFile getContentRoot() {
    File file = getTestContentFile();
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  @Override
  protected String getTestDirectoryName() {
    return getTestName(true);
  }


  protected PsiDirectory getPackageDirectory(final String packageRelativePath) {
    return getPsiManager().findDirectory(getContentRoot().findFileByRelativePath("src/" + packageRelativePath));
  }

  protected PsiDirectory getSrcDirectory() {
    return getPsiManager().findDirectory(getContentRoot().findFileByRelativePath("src"));
  }

  protected PsiDirectory getContentDirectory() {
    return getPsiManager().findDirectory(getContentRoot());
  }
  
  protected String getRootFiles() {
    return " " + myModule.getModuleDir().getName() + "\n" +
           " " + myProject.getName() + " "+ "\n";
  }
}
