package com.intellij.psi.impl.cache.impl;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import consulo.application.ApplicationManager;
import consulo.ide.impl.idea.openapi.roots.ModuleRootModificationUtil;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

/**
 * @author max
 */
public abstract class SCR17650Test extends PsiTestCase {
  private static final String TEST_ROOT = "/psi/repositoryUse/cls";

  private VirtualFile myDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = FileUtil.createTempFile(getTestName(true), "");
    root.delete();
    root.mkdir();
    myFilesToDelete.add(root);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile rootVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

          myDir = rootVFile.createChildDirectory(null, "contentAndLibrary");

          VirtualFile file1 = myDir.createChildData(null, "A.java");
          VfsUtil.saveText(file1, "package p; public class A{ public void foo(); }");
          VfsUtilCore.copyFile(null, getClassFile(), myDir);

          PsiTestUtil.addSourceRoot(myModule, myDir);
          ModuleRootModificationUtil.addModuleLibrary(myModule, myDir.getUrl());
        }
        catch (IOException e) {
          LOGGER.error(e);
        }
      }
    });
  }

  private static VirtualFile getClassFile() {
    VirtualFile vDir = LocalFileSystem.getInstance().findFileByPath(TEST_ROOT.replace(File.separatorChar, '/'));
    VirtualFile child = vDir.findChild("pack").findChild("MyClass.class");
    return child;
  }

  public void test17650() throws Exception {
    assertEquals("p.A", myJavaFacade.findClass("p.A").getQualifiedName());
    assertEquals("pack.MyClass", myJavaFacade.findClass("pack.MyClass").getQualifiedName());
  }
}
