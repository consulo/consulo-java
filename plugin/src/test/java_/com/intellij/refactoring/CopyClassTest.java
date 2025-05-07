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
package com.intellij.refactoring;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import consulo.document.FileDocumentManager;
import consulo.java.language.module.util.JavaClassNames;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.java.impl.refactoring.copy.CopyClassesHandler;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import consulo.language.util.IncorrectOperationException;

/**
 * @author yole
 */
public abstract class CopyClassTest extends CodeInsightTestCase {
  private VirtualFile myRootDir;

  public void testReplaceAllOccurrences() throws Exception {
    doTest("Foo", "Bar");
  }

  public void testLibraryClass() throws Exception {  // IDEADEV-28791
    doTest(JavaClassNames.JAVA_UTIL_ARRAY_LIST, "Bar");
  }

  private void doTest(final String oldName, final String copyName) throws Exception {
    String root = JavaTestUtil.getJavaTestDataPath() + "/refactoring/copyClass/" + getTestName(true);

    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17("java 1.5"));
    myRootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);

    PsiElement element = performAction(oldName, copyName);

    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    FileDocumentManager.getInstance().saveAllDocuments();

    VirtualFile fileAfter = myRootDir.findChild(copyName + ".java");
    VirtualFile fileExpected = myRootDir.findChild(copyName + ".expected.java");

    PlatformTestUtil.assertFilesEqual(fileExpected, fileAfter);
  }

  private PsiElement performAction(final String oldName, final String copyName) throws IncorrectOperationException {
    PsiClass oldClass = JavaPsiFacade.getInstance(myProject).findClass(oldName, ProjectScope.getAllScope(myProject));
    return CopyClassesHandler.doCopyClasses(Collections.singletonMap(oldClass.getNavigationElement().getContainingFile(), new PsiClass[]{oldClass}), copyName, myPsiManager.findDirectory(myRootDir),
                                     myProject);
  }

  public void testPackageLocalClasses() throws Exception {
    doMultifileTest();
  }

  public void testPackageLocalMethods() throws Exception {
    doMultifileTest();
  }

  //copy all classes from p1 -> p2
  private void doMultifileTest() throws Exception {
    String root = JavaTestUtil.getJavaTestDataPath() + "/refactoring/copyClass/multifile/" + getTestName(true);
    String rootBefore = root + "/before";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);

    final HashMap<PsiFile, PsiClass[]> map = new HashMap<PsiFile, PsiClass[]>();
    final VirtualFile sourceDir = rootDir.findChild("p1");
    for (VirtualFile file : sourceDir.getChildren()) {
      final PsiFile psiFile = myPsiManager.findFile(file);
      if (psiFile instanceof PsiJavaFile) {
        map.put(psiFile, ((PsiJavaFile)psiFile).getClasses());
      }
    }

    final VirtualFile targetVDir = rootDir.findChild("p2");
    CopyClassesHandler.doCopyClasses(map, null, myPsiManager.findDirectory(targetVDir), myProject);

    String rootAfter = root + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
  }

  public void testPackageHierarchy() throws Exception {
    doPackageCopy();
  }

  public void testPackageOneLevelHierarchy() throws Exception {
    doPackageCopy();
  }

  private void doPackageCopy() throws Exception {
    String root = JavaTestUtil.getJavaTestDataPath() + "/refactoring/copyClass/multifile/" + getTestName(true);
    String rootBefore = root + "/before";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);

    final VirtualFile targetVDir = rootDir.findChild("p2");
    final PsiDirectory sourceP1Dir = myPsiManager.findDirectory(rootDir.findChild("p1"));
    final PsiDirectory targetP2Dir = myPsiManager.findDirectory(targetVDir);
    new CopyClassesHandler().doCopy(new PsiElement[]{sourceP1Dir}, targetP2Dir);

    String rootAfter = root + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
  }
}
