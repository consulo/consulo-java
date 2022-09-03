package com.intellij.psi.impl.cache.impl;

import java.io.File;
import java.io.IOException;

import consulo.application.ApplicationManager;
import consulo.ide.impl.idea.openapi.roots.ModuleRootModificationUtil;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

/**
 * @author max
 */
public abstract class SCR19174Test extends PsiTestCase {
  private VirtualFile myDir;
  private VirtualFile myVFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = FileUtil.createTempFile(getTestName(false), "");
    root.delete();
    root.mkdir();
    myFilesToDelete.add(root);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile rootVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

          myDir = rootVFile.createChildDirectory(null, "contentAndLibrary");

          /*
          myVFile = myDir.createChildData(null, "A.java");
          Writer writer1 = myVFile.getWriter(null);
          writer1.write("package p; public class A{ public void foo(); }");
          writer1.close();
          */
          PsiTestUtil.addSourceRoot(myModule, myDir);
        }
        catch (IOException e) {
          LOGGER.error(e);
        }
      }
    });
  }

  private void changeRoots() {
    ModuleRootModificationUtil.addModuleLibrary(myModule, myDir.getUrl());
  }

  public void testBug() throws Exception {
    touchFileSync();
    PsiFile psiFile = myPsiManager.findFile(myVFile);
    psiFile.getText();
    changeRoots();
  }

  private void touchFileSync() throws IOException {
    myVFile = myDir.createChildData(null, "A.java");
    VfsUtil.saveText(myVFile, "package p; public class A{ public void foo(); }");
  }
}
