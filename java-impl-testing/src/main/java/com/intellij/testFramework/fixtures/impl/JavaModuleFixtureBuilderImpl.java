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

package com.intellij.testFramework.fixtures.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import consulo.virtualFileSystem.LocalFileSystem;
import org.jetbrains.annotations.NonNls;
import consulo.module.Module;
import com.intellij.java.language.projectRoots.JavaSdk;
import consulo.content.bundle.Sdk;
import consulo.content.impl.internal.bundle.SdkImpl;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.content.OrderRootType;
import consulo.content.library.Library;
import consulo.content.library.LibraryTable;
import consulo.application.util.SystemInfo;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import consulo.util.collection.ArrayUtil;
import consulo.content.bundle.SdkPointerManager;
import consulo.java.language.module.extension.JavaMutableModuleExtension;
import consulo.util.pointers.NamedPointer;
import consulo.util.pointers.NamedPointerImpl;

/**
 * @author mike
 */
abstract class JavaModuleFixtureBuilderImpl<T extends ModuleFixture> extends ModuleFixtureBuilderImpl<T> implements JavaModuleFixtureBuilder<T> {
  private static class MockWrappedSdkPointerManager implements SdkPointerManager
  {
    private Sdk mySdk;

    private MockWrappedSdkPointerManager(Sdk sdk) {
      mySdk = sdk;
    }

    @Nonnull
    @Override
    public NamedPointer<Sdk> create(@Nonnull String name) {
      return new NamedPointerImpl<Sdk>(mySdk);
    }

    @Nonnull
    @Override
    public NamedPointer<Sdk> create(@Nonnull Sdk value) {
      return new NamedPointerImpl<Sdk>(mySdk);
    }
  }

  @NonNls
  public static final String TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME = "tests.external.compiler.home";
  private final List<Lib> myLibraries = new ArrayList<Lib>();
  private String myJdk;
  private MockJdkLevel myMockJdkLevel = MockJdkLevel.jdk14;
  private LanguageLevel myLanguageLevel = null;

  public JavaModuleFixtureBuilderImpl(final TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(fixtureBuilder);
  }

  @Override
  public JavaModuleFixtureBuilder setLanguageLevel(final LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
    return this;
  }

  @Override
  public JavaModuleFixtureBuilder addLibrary(String libraryName, String... classPath) {
    final HashMap<OrderRootType, String[]> map = new HashMap<OrderRootType, String[]>();
    for (String path : classPath) {
      if (!new File(path).exists()) {
        System.out.println(path + " not exists");
      }
    }
    map.put(OrderRootType.CLASSES, classPath);
    myLibraries.add(new Lib(libraryName, map));
    return this;
  }

  @Override
  public JavaModuleFixtureBuilder addLibrary(@NonNls final String libraryName, final Map<OrderRootType, String[]> roots) {
    myLibraries.add(new Lib(libraryName, roots));
    return this;
  }

  @Override
  public JavaModuleFixtureBuilder addLibraryJars(String libraryName, String basePath, String... jars) {
    if (!basePath.endsWith("/")) {
      basePath += "/";
    }
    String[] classPath = ArrayUtil.newStringArray(jars.length);
    for (int i = 0; i < jars.length; i++) {
      classPath[i] = basePath + jars[i];
    }
    return addLibrary(libraryName, classPath);
  }

  @Override
  public JavaModuleFixtureBuilder addJdk(String jdkPath) {
    myJdk = jdkPath;
    return this;
  }

  @Override
  public void setMockJdkLevel(final MockJdkLevel level) {
    myMockJdkLevel = level;
  }

  @Override
  protected void initModule(final Module module) {
    super.initModule(module);

    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final LibraryTable libraryTable = model.getModuleLibraryTable();

    for (Lib lib : myLibraries) {
      String libraryName = lib.getName();

      final Library library = libraryTable.createLibrary(libraryName);

      final Library.ModifiableModel libraryModel = library.getModifiableModel();

      for (OrderRootType rootType : OrderRootType.getAllTypes()) {
        final String[] roots = lib.getRoots(rootType);
        for (String root : roots) {
          VirtualFile vRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
          if (vRoot != null && OrderRootType.CLASSES.equals(rootType) && !vRoot.isDirectory()) {
            final VirtualFile jar = StandardFileSystems.jar().refreshAndFindFileByPath(root + "!/");
            if (jar != null) vRoot = jar;
          }
          if (vRoot != null) {
            libraryModel.addRoot(vRoot, rootType);
          }
        }
      }
      libraryModel.commit();
    }

    final Sdk jdk;
    if (myJdk != null) {
      jdk = JavaSdk.getInstance().createJdk(module.getName() + "_jdk", myJdk, false);
      ((SdkImpl)jdk).setVersionString("java 1.5");
    }
    else {
      jdk = IdeaTestUtil.getMockJdk17();
    }

    JavaMutableModuleExtension moduleExtension = model.getExtensionWithoutCheck(JavaMutableModuleExtension.class);
    assert moduleExtension != null;
    moduleExtension.setEnabled(true);
    /*if (jdk != null) {
      moduleExtension.getInheritableSdk().set(new MockSdkWrapper(getTestsExternalCompilerHome(), jdk));
    }   */

    if (myMockJdkLevel == MockJdkLevel.jdk15) {
      myLanguageLevel = LanguageLevel.JDK_1_5;
    }

    if(myLanguageLevel != null) {
      moduleExtension.getInheritableLanguageLevel().set(null, myLanguageLevel.getName());
    }
    model.commit();

    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        Library library = ((LibraryOrderEntry)entry).getLibrary();
        libraryCreated(library, module);
      }
    }
  }

  @Override
  protected void setupRootModel(ModifiableRootModel rootModel) {
   /* if (myOutputPath != null) {
      final File pathFile = new File(myOutputPath);
      if (!pathFile.mkdirs()) {
        assert pathFile.exists() : "unable to create: " + myOutputPath;
      }
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(myOutputPath);
      assert virtualFile != null : "cannot find output path: " + myOutputPath;
      rootModel.getModuleExtensionOld(CompilerModuleExtension.class).setCompilerOutputPath(virtualFile);
      rootModel.getModuleExtensionOld(CompilerModuleExtension.class).inheritCompilerOutputPath(false);
      rootModel.getModuleExtensionOld(CompilerModuleExtension.class).setExcludeOutput(false);
    }
    if (myTestOutputPath != null) {
      assert new File(myTestOutputPath).mkdirs() : myTestOutputPath;
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(myTestOutputPath);
      assert virtualFile != null : "cannot find test output path: " + myTestOutputPath;
      rootModel.getModuleExtensionOld(CompilerModuleExtension.class).setCompilerOutputPathForTests(virtualFile);
      rootModel.getModuleExtensionOld(CompilerModuleExtension.class).inheritCompilerOutputPath(false);
      rootModel.getModuleExtensionOld(CompilerModuleExtension.class).setExcludeOutput(false);
    } */
  }

  public static String getTestsExternalCompilerHome() {
    String compilerHome = System.getProperty(TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME, null);
    if (compilerHome == null) {
      if (SystemInfo.isMac) {
        compilerHome = new File(System.getProperty("java.home")).getAbsolutePath();
      }
      else {
        compilerHome = new File(System.getProperty("java.home")).getParentFile().getAbsolutePath();
      }
    }
    return compilerHome;
  }

  protected void libraryCreated(Library library, Module module) {}

  private static class Lib {
    private final String myName;
    private final Map<OrderRootType, String []> myRoots;

    public Lib(final String name, final Map<OrderRootType, String[]> roots) {
      myName = name;
      myRoots = roots;
    }

    public String getName() {
      return myName;
    }

    public String [] getRoots(OrderRootType rootType) {
      final String[] roots = myRoots.get(rootType);
      return roots != null ? roots : ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }
}
