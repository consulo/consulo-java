package com.intellij.roots;

import consulo.module.Module;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.content.OrderRootType;
import consulo.content.library.Library;
import consulo.content.library.LibraryTablesRegistrar;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import consulo.virtualFileSystem.util.PathsList;
import consulo.container.boot.ContainerPathManager;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

/**
 * @author nik
 */
public abstract class ModuleRootManagerTestCase extends ModuleTestCase
{
  protected static void assertRoots(PathsList pathsList, VirtualFile... files) {
    assertOrderedEquals(pathsList.getRootDirs(), files);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    Sdk jdk = super.getTestProjectJdk();
    SdkModificator modificator = jdk.getSdkModificator();
    VirtualFile rtJar = null;
    for (VirtualFile root : modificator.getRoots(OrderRootType.CLASSES)) {
      if (root.getName().equals("rt.jar")) {
        rtJar = root;
        break;
      }
    }
    assertNotNull("rt.jar not found in jdk: " + jdk, rtJar);
    modificator.removeAllRoots();
    modificator.addRoot(rtJar, OrderRootType.CLASSES);
    modificator.commitChanges();
    return jdk;
  }

  protected VirtualFile getRtJar() {
    return getTestProjectJdk().getRootProvider().getFiles(OrderRootType.CLASSES)[0];
  }

  protected VirtualFile getJDomJar() {
    return getJarFromLibDir("jdom.jar");
  }

  protected VirtualFile getJDomSources() {
    return getJarFromLibDir("src/jdom.zip");
  }


  protected VirtualFile getJarFromLibDir(String name) {
    VirtualFile file = getVirtualFile(ContainerPathManager.get().findFileInLibDirectory(name));
    assertNotNull(name + " not found", file);
    VirtualFile jarFile = ArchiveVfsUtil.getJarRootForLocalFile(file);
    assertNotNull(name + " is not jar", jarFile);
    return jarFile;
  }

  protected VirtualFile addSourceRoot(Module module, boolean testSource) throws IOException {
    VirtualFile root = getVirtualFile(createTempDir(module.getName() + (testSource ? "Test" : "Prod") + "Src"));
    PsiTestUtil.addSourceContentToRoots(module, root, testSource);
    return root;
  }

  protected VirtualFile setModuleOutput(Module module, boolean test) throws IOException {
    VirtualFile output = getVirtualFile(createTempDir(module.getName() + (test ? "Test" : "Prod") + "Output"));
    PsiTestUtil.setCompilerOutputPath(module, output != null ? output.getUrl() : null, test);
    return output;
  }

  protected Library createLibrary(String name, VirtualFile classesRoot, VirtualFile sourceRoot) {
    Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).createLibrary(name);
    Library.ModifiableModel model = library.getModifiableModel();
    model.addRoot(classesRoot, OrderRootType.CLASSES);
    if (sourceRoot != null) {
      model.addRoot(sourceRoot, OrderRootType.SOURCES);
    }
    model.commit();
    return library;
  }

  protected Library createJDomLibrary() {
    return createLibrary("jdom", getJDomJar(), getJDomSources());
  }

  protected Library createAsmLibrary() {
    return createLibrary("asm", getAsmJar(), null);
  }

  protected VirtualFile getAsmJar() {
    return getJarFromLibDir("asm.jar");
  }
}
