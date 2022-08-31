package com.intellij.psi;

import static org.junit.Assert.assertNotNull;

import java.io.File;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;

/**
 *  @author dsl
 */
public abstract class SCR17094Test extends PsiTestCase {
  protected void setUpClasses(final String s) throws Exception {
    final String testRoot = "/psi/repositoryUse/scr17094";
    VirtualFile classesRoot  = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        String path = testRoot + "/" + s;
        path = path.replace(File.separatorChar, '/');
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      }
    });
    assertNotNull(classesRoot);
    ModuleRootModificationUtil.addModuleLibrary(myModule, classesRoot.getUrl());
  }

  public void testSRC() throws Exception {
    setUpClasses("classes");
    final JavaPsiFacade psiManager = getJavaFacade();
    final PsiClass classA = psiManager.findClass("a.a.a.a.e.f.i", GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(classA);
  }

  public void test3() throws Exception {
    setUpClasses("classes2");
    final JavaPsiFacade psiManager = getJavaFacade();
    final PsiClass classA = psiManager.findClass("com.intellij.internal.f.a.b.a.i", GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(classA);
  }
}
