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

import com.intellij.JavaTestUtil;
import consulo.application.ApplicationManager;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.VirtualFileAdapter;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.VirtualFileManager;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import com.intellij.testFramework.PsiTestUtil;
import junit.framework.Assert;

import java.util.Arrays;
import java.util.Comparator;

public abstract class MovePackageAsDirectoryTest extends MultiFileTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/movePackageAsDir/";
  }

  public void testMovePackage() throws Exception {
    doTest(createAction("pack1", "target"));
  }

  public void testMovePackageWithTxtFilesInside() throws Exception {
    doTest(createAction("pack1", "target"));
  }

  public void testMultipleClassesInOneFile() throws Exception {
    final boolean [] fileWasDeleted = new boolean[]{false};
    VirtualFileAdapter fileAdapter = new VirtualFileAdapter() {
      @Override
      public void fileDeleted(VirtualFileEvent event) {
        fileWasDeleted[0] = !event.getFile().isDirectory();
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(fileAdapter);
    try {
      doTest(createAction("pack1", "target"));
    }
    finally {
      VirtualFileManager.getInstance().removeVirtualFileListener(fileAdapter);
    }
    Assert.assertFalse("Deleted instead of moved", fileWasDeleted[0]);
  }


  public void testRemoveUnresolvedImports() throws Exception {
    doTest(createAction("pack1", "target"));
  }

  public void testXmlDirRefs() throws Exception {
    doTest(createAction("pack1", "target"));
  }

  public void testXmlEmptyDirRefs() throws Exception {
    final String packageName = "pack1";
    doTest(new MyPerformAction(packageName, "target"){
      private static final String EMPTY_TXT = "empty.txt";
      @Override
      protected void preprocessSrcDir(PsiDirectory srcDirectory) {
        final PsiFile empty = srcDirectory.findFile(EMPTY_TXT);
        assert empty != null;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            empty.delete();
          }
        });
      }

      @Override
      protected void postProcessTargetDir(PsiDirectory targetDirectory) {
        final PsiDirectory subdirectory = targetDirectory.findSubdirectory(packageName);
        assert subdirectory != null;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            subdirectory.createFile(EMPTY_TXT);
          }
        });
      }
    });
  }

  private PerformAction createAction(String packageName, String targetPackageName) {
    return new MyPerformAction(packageName, targetPackageName);
  }

  @Override
  protected void prepareProject(VirtualFile rootDir) {
    PsiTestUtil.addContentRoot(myModule, rootDir);
    VirtualFile[] children = rootDir.getChildren();
    for (VirtualFile child : children) {
      if (child.getName().startsWith("src")) {
        PsiTestUtil.addSourceRoot(myModule, child);
      }
    }
  }

  private class MyPerformAction implements PerformAction {
    private final String myPackageName;
    private final String myTargetPackageName;

    public MyPerformAction(String packageName, String targetPackageName) {
      myPackageName = packageName;
      myTargetPackageName = targetPackageName;
    }

    @Override
    public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
      Comparator<PsiDirectory> directoryComparator = new Comparator<PsiDirectory>() {
        @Override
        public int compare(PsiDirectory o1, PsiDirectory o2) {
          return o1.getVirtualFile().getPresentableUrl().compareTo(o2.getVirtualFile().getPresentableUrl());
        }
      };

      PsiJavaPackage sourcePackage = psiFacade.findPackage(myPackageName);
      assertNotNull(sourcePackage);
      PsiDirectory[] srcDirectories = sourcePackage.getDirectories();
      assertEquals(srcDirectories.length, 2);
      Arrays.sort(srcDirectories, directoryComparator);

      PsiJavaPackage targetPackage = psiFacade.findPackage(myTargetPackageName);
      assertNotNull(targetPackage);
      PsiDirectory[] targetDirectories = targetPackage.getDirectories();
      Arrays.sort(targetDirectories, directoryComparator);
      assertTrue(targetDirectories.length > 0);
      preprocessSrcDir(srcDirectories[0]);
      new MoveDirectoryWithClassesProcessor(getProject(), new PsiDirectory[]{srcDirectories[0]}, targetDirectories[0], false, false, true, null).run();
      postProcessTargetDir(targetDirectories[0]);
      FileDocumentManager.getInstance().saveAllDocuments();
    }

    protected void postProcessTargetDir(PsiDirectory targetDirectory) {
    }

    protected void preprocessSrcDir(PsiDirectory srcDirectory) {
    }
  }
}
