package com.intellij.roots;

import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.PathsList;

/**
 * @author nik
 */
public abstract class OrderEntriesTest extends ModuleRootManagerTestCase {
  public void testLibrary() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());
    assertOrderFiles(OrderRootType.CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, getJDomSources());
  }

  public void testModuleSources() throws Exception {
    VirtualFile srcRoot = addSourceRoot(myModule, false);
    VirtualFile testRoot = addSourceRoot(myModule, true);

    assertOrderFiles(OrderRootType.CLASSES, getRtJar());
    assertOrderFiles(OrderRootType.SOURCES, srcRoot, testRoot);
  }

  public void testLibraryScope() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary(), DependencyScope.TEST, false);

    assertOrderFiles(OrderRootType.CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, getJDomSources());
  }

  public void testModuleDependency() throws Exception {
    Module dep = createModule("dep");
    VirtualFile srcRoot = addSourceRoot(dep, false);
    VirtualFile testRoot = addSourceRoot(dep, true);
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, false);

    assertOrderFiles(OrderRootType.CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, srcRoot, testRoot, getJDomSources());
  }

  public void testModuleDependencyScope() throws Exception {
    Module dep = createModule("dep");
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.TEST, true);

    assertOrderFiles(OrderRootType.CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, getJDomSources());
  }

  public void testNotExportedLibraryDependency() throws Exception {
    Module dep = createModule("dep");
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, false);
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, false);

    assertOrderFiles(OrderRootType.CLASSES, getRtJar());
    assertOrderFiles(OrderRootType.SOURCES);
  }

  private void assertOrderFiles(OrderRootType type, VirtualFile... files) {
    assertRoots(collectByOrderEnumerator(type), files);
  }

  private PathsList collectByOrderEnumerator(OrderRootType type) {
    OrderEnumerator base = OrderEnumerator.orderEntries(myModule);
    if (type == OrderRootType.CLASSES) {
      return base.withoutModuleSourceEntries().recursively().exportedOnly().getPathsList();
    }
    if (type == OrderRootType.SOURCES) {
      return base.recursively().exportedOnly().getSourcePathsList();
    }
    throw new AssertionError(type);
  }
}

