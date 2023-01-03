package com.intellij.psi.impl.cache.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import consulo.application.ApplicationManager;
import consulo.document.FileDocumentManager;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.ModuleRootManager;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import consulo.java.impl.module.extension.JavaModuleExtensionImpl;
import consulo.java.impl.module.extension.JavaMutableModuleExtensionImpl;
import consulo.roots.ContentFolderScopes;

/**
 * @author max
 */
public abstract class SCR14423Test extends PsiTestCase {
  private VirtualFile myPrjDir1;
  private VirtualFile mySrcDir1;
  private VirtualFile myPackDir;

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
          VirtualFile rootVFile =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

          myPrjDir1 = rootVFile.createChildDirectory(null, "prj1");
          mySrcDir1 = myPrjDir1.createChildDirectory(null, "src1");

          myPackDir = mySrcDir1.createChildDirectory(null, "p");
          VirtualFile file1 = myPackDir.createChildData(null, "A.java");
          VfsUtil.saveText(file1, "package p; public class A{ public void foo(); }");

          PsiTestUtil.addContentRoot(myModule, myPrjDir1);
          PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
        }
        catch (IOException e) {
          LOGGER.error(e);
        }
      }
    });
  }

  public void testBug2() throws Exception {
    PsiClass psiClass = myJavaFacade.findClass("p.A");
    assertEquals("p.A", psiClass.getQualifiedName());

    testBug1();
  }

  public void testBug1() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiTestUtil.addExcludedRoot(myModule, myPackDir);

        PsiClass psiClass = myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject));
        assertNull(psiClass);

        ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
        final ContentEntry content = rootModel.getContentEntries()[0];
        content.removeFolder(content.getFolders(ContentFolderScopes.excluded())[0]);
        rootModel.commit();

        psiClass = myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject));
        assertEquals("p.A", psiClass.getQualifiedName());
      }
    });
  }

  public void testBug3() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiClass psiClass = myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject));
        assertEquals("p.A", psiClass.getQualifiedName());

        assertTrue(psiClass.isValid());

        PsiTestUtil.addExcludedRoot(myModule, myPackDir);

        assertFalse(psiClass.isValid());

        ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
        final ContentEntry content = rootModel.getContentEntries()[0];
        content.removeFolder(content.getFolders(ContentFolderScopes.excluded())[0]);
        rootModel.commit();

        psiClass = myJavaFacade.findClass("p.A");
        assertTrue(psiClass.isValid());
      }
    });
  }

  public void testSyncrhonizationAfterChange() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        PsiClass psiClass = myJavaFacade.findClass("p.A");
        final VirtualFile vFile = psiClass.getContainingFile().getVirtualFile();
        File ioFile = consulo.ide.impl.idea.openapi.vfs.VfsUtil.virtualToIoFile(vFile);
        ioFile.setLastModified(5);

        LocalFileSystem.getInstance().refresh(false);

        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
        final ModifiableRootModel modifiableModel = moduleRootManager.getModifiableModel();
        JavaMutableModuleExtensionImpl
          javaModuleExtension = (JavaMutableModuleExtensionImpl)modifiableModel.getExtension(JavaModuleExtensionImpl.class);
        assert javaModuleExtension != null;
        javaModuleExtension.getInheritableSdk().set((String) null, null);
        modifiableModel.commit();

        psiClass = myJavaFacade.findClass("p.A");
        assertNotNull(psiClass);
      }
    });
  }
}
