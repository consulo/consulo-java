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
package com.intellij.psi;

import com.intellij.JavaTestUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.application.ApplicationManager;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.testFramework.PsiTestCase;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *  @author dsl
 */
public abstract class LibraryOrderTest extends PsiTestCase {

  public void test1() {
    setupPaths();
    checkClassFromLib("test.A", "1");

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    OrderEntry[] order = rootModel.getOrderEntries();
    int length = order.length;
    OrderEntry lib2 = order[length - 1];
    OrderEntry lib1 = order[length - 2];
    assertTrue(lib1 instanceof LibraryOrderEntry);
    assertEquals("lib1", ((LibraryOrderEntry) lib1).getLibraryName());
    assertTrue(lib2 instanceof LibraryOrderEntry);
    assertEquals("lib2", ((LibraryOrderEntry) lib2).getLibraryName());

    order[length - 1] = lib1;
    order[length - 2] = lib2;
    rootModel.rearrangeOrderEntries(order);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        rootModel.commit();
      }
    }
    );

    checkClassFromLib("test.A", "2");
  }

  public void testNavigation() throws Exception {
    setupPaths();
    PsiClass classA = getJavaFacade().findClass("test.A");
    PsiElement navigationElement = classA.getNavigationElement();
    assertNotNull(navigationElement);
    assertTrue(navigationElement != classA);
    assertEquals("A.java", navigationElement.getContainingFile().getVirtualFile().getName());
  }

  private void checkClassFromLib(String qualifiedName, String index) {
    PsiClass classA = (PsiClass)getJavaFacade().findClass(qualifiedName).getNavigationElement();
    assertNotNull(classA);
    PsiMethod[] methodsA = classA.getMethods();
    assertEquals(1, methodsA.length);
    assertEquals("methodOfClassFromLib" + index, methodsA[0].getName());
  }

  public void setupPaths() {
    String basePath = JavaTestUtil.getJavaTestDataPath() + "/psi/libraryOrder/";

    VirtualFile lib1SrcFile = refreshAndFindFile(basePath + "lib1/src");
    VirtualFile lib1classes = refreshAndFindFile(basePath + "lib1/classes");
    VirtualFile lib2SrcFile = refreshAndFindFile(basePath + "lib2/src");
    VirtualFile lib2classes = refreshAndFindFile(basePath + "lib2/classes");

    assertTrue(lib1SrcFile != null);
    assertTrue(lib2SrcFile != null);

    addLibraryWithSourcePath("lib1", lib1classes, lib1SrcFile);
    addLibraryWithSourcePath("lib2", lib2classes, lib2SrcFile);

    List<VirtualFile> list = Arrays.asList(OrderEnumerator.orderEntries(myModule).getClassesRoots());
    assertTrue(list.contains(lib1classes));
    assertTrue(list.contains(lib2classes));
  }

  private VirtualFile refreshAndFindFile(String path) {
    File ioLib1Src = new File(path);
    VirtualFile lib1SrcFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioLib1Src);
    return lib1SrcFile;
  }

  private void addLibraryWithSourcePath(String name, VirtualFile libClasses, VirtualFile libSource) {
    ModuleRootModificationUtil.addModuleLibrary(myModule, name, Collections.singletonList(libClasses.getUrl()),
                                                Collections.singletonList(libSource.getUrl()));
  }
}
