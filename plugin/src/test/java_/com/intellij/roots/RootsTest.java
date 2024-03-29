package com.intellij.roots;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import consulo.application.ApplicationManager;
import consulo.module.content.ModuleRootManager;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

/**
 *  @author dsl
 */
public abstract class RootsTest extends PsiTestCase {
  public void testTest1() {
    final String rootPath = "/moduleRootManager/roots/" + "test1";
    final VirtualFile[] rootFileBox = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        rootFileBox[0] =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath.replace(File.separatorChar, '/'));
      }
    });
    final VirtualFile rootFile = rootFileBox[0];
    final VirtualFile classesFile = rootFile.findChild("classes");
    assertNotNull(classesFile);
    final VirtualFile childOfContent = rootFile.findChild("x.txt");
    assertNotNull(childOfContent);
    final VirtualFile childOfClasses = classesFile.findChild("y.txt");
    assertNotNull(childOfClasses);

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);


    PsiTestUtil.addContentRoot(myModule, rootFile);
    PsiTestUtil.setCompilerOutputPath(myModule, classesFile.getUrl(), false);
    PsiTestUtil.setExcludeCompileOutput(myModule, false);
    assertTrue(rootManager.getFileIndex().isInContent(childOfContent));
    assertTrue(rootManager.getFileIndex().isInContent(childOfClasses));

    PsiTestUtil.setExcludeCompileOutput(myModule, true);
    assertTrue(rootManager.getFileIndex().isInContent(childOfContent));
    assertFalse(rootManager.getFileIndex().isInContent(childOfClasses));
  }

}
