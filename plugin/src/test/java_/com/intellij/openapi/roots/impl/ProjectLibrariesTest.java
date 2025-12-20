package com.intellij.openapi.roots.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;

import consulo.application.ApplicationManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.ModuleRootManager;
import consulo.ide.impl.idea.openapi.roots.ModuleRootModificationUtil;
import consulo.content.OrderRootType;
import consulo.ide.impl.idea.openapi.roots.impl.libraries.ProjectLibraryTable;
import consulo.content.library.Library;
import consulo.content.library.LibraryTable;
import consulo.application.util.function.Computable;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestCase;

/**
 * @author dsl
 */
public abstract class ProjectLibrariesTest extends IdeaTestCase {
  public void test() {
    final LibraryTable libraryTable = ProjectLibraryTable.getInstance(myProject);
    Library lib = ApplicationManager.getApplication().runWriteAction(new Computable<Library>() {
      @Override
      public Library compute() {
        return libraryTable.createLibrary("LIB");
      }
    });
    ModuleRootModificationUtil.addDependency(myModule, lib);
    JavaPsiFacade manager = getJavaFacade();
    assertNull(manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));
    final File file = new File("/psi/repositoryUse/cls");
    final VirtualFile root = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      }
    });
    assertNotNull(root);
    final Library.ModifiableModel modifyableModel = lib.getModifiableModel();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        modifyableModel.addRoot(root, OrderRootType.CLASSES);
        modifyableModel.commit();
      }
    });
    PsiClass aClass = manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(aClass);
  }

  public void test1() {
    final LibraryTable libraryTable = ProjectLibraryTable.getInstance(myProject);
    Library lib = ApplicationManager.getApplication().runWriteAction(new Computable<Library>() {
      @Override
      public Library compute() {
        return libraryTable.createLibrary("LIB");
      }
    });
    ModuleRootModificationUtil.addDependency(myModule, lib);
    JavaPsiFacade manager = getJavaFacade();
    assertNull(manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));

    final ModifiableRootModel rootModel2 = ModuleRootManager.getInstance(myModule).getModifiableModel();
    assertNotNull(rootModel2.findLibraryOrderEntry(lib));
    final File file = new File("/psi/repositoryUse/cls");
    final VirtualFile root = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      }
    });
    assertNotNull(root);
    final Library.ModifiableModel modifyableModel = lib.getModifiableModel();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        modifyableModel.addRoot(root, OrderRootType.CLASSES);
        modifyableModel.commit();
      }
    });
    PsiClass aClass = manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(aClass);
    assertTrue(Arrays.asList(rootModel2.orderEntries().librariesOnly().classes().getRoots()).contains(root));
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        rootModel2.commit();
      }
    });
    PsiClass aClass1 = manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(aClass1);
  }
}
