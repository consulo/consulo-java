package com.intellij.roots;

import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import consulo.util.collection.ArrayUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static consulo.module.content.layer.OrderEnumerator.orderEntries;


/**
 * @author nik
 */
public abstract class OrderEnumeratorTest extends ModuleRootManagerTestCase {

  public void testLibrary() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());

    assertClassRoots(orderEntries(myModule), getRtJar(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().productionOnly().runtimeOnly(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutLibraries(), getRtJar());
    assertSourceRoots(orderEntries(myModule), getJDomSources());
  }

  public void testModuleSources() throws Exception {
    final VirtualFile srcRoot = addSourceRoot(myModule, false);
    final VirtualFile testRoot = addSourceRoot(myModule, true);
    final VirtualFile output = setModuleOutput(myModule, false);
    final VirtualFile testOutput = setModuleOutput(myModule, true);

    assertClassRoots(orderEntries(myModule).withoutSdk(), testOutput, output);
    assertClassRoots(orderEntries(myModule).withoutSdk().productionOnly(), output);
    assertSourceRoots(orderEntries(myModule), srcRoot, testRoot);
    assertSourceRoots(orderEntries(myModule).productionOnly(), srcRoot);

    assertEnumeratorRoots(orderEntries(myModule).withoutSdk().classes().withoutSelfModuleOutput(), output);
    assertEnumeratorRoots(orderEntries(myModule).withoutSdk().productionOnly().classes().withoutSelfModuleOutput());
  }

  public void testLibraryScope() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary(), DependencyScope.RUNTIME, false);

    assertClassRoots(orderEntries(myModule).withoutSdk(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().exportedOnly());
    assertClassRoots(orderEntries(myModule).withoutSdk().compileOnly());
  }

  public void testModuleDependency() throws Exception {
    final Module dep = createModule("dep");
    final VirtualFile depSrcRoot = addSourceRoot(dep, false);
    final VirtualFile depTestRoot = addSourceRoot(dep, true);
    final VirtualFile depOutput = setModuleOutput(dep, false);
    final VirtualFile depTestOutput = setModuleOutput(dep, true);
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, true);

    final VirtualFile srcRoot = addSourceRoot(myModule, false);
    final VirtualFile testRoot = addSourceRoot(myModule, true);
    final VirtualFile output = setModuleOutput(myModule, false);
    final VirtualFile testOutput = setModuleOutput(myModule, true);

    assertClassRoots(orderEntries(myModule).withoutSdk(), testOutput, output, depTestOutput, depOutput);
    assertClassRoots(orderEntries(myModule).withoutSdk().recursively(), testOutput, output, depTestOutput, depOutput, getJDomJar());
    assertSourceRoots(orderEntries(myModule), srcRoot, testRoot, depSrcRoot, depTestRoot);
    assertSourceRoots(orderEntries(myModule).recursively(), srcRoot, testRoot, depSrcRoot, depTestRoot, getJDomSources());

    assertClassRoots(orderEntries(myModule).withoutSdk().withoutModuleSourceEntries().recursively(), getJDomJar());
    assertSourceRoots(orderEntries(myModule).withoutSdk().withoutModuleSourceEntries().recursively(), getJDomSources());
    assertEnumeratorRoots(orderEntries(myModule).withoutSdk().withoutModuleSourceEntries().recursively().classes(), getJDomJar());
    assertEnumeratorRoots(orderEntries(myModule).withoutSdk().withoutModuleSourceEntries().recursively().sources(), getJDomSources());

    assertEnumeratorRoots(orderEntries(myModule).withoutSdk().recursively().classes().withoutSelfModuleOutput(),
                          output, depTestOutput, depOutput, getJDomJar());
    assertEnumeratorRoots(orderEntries(myModule).productionOnly().withoutSdk().recursively().classes().withoutSelfModuleOutput(),
                          depOutput, getJDomJar());

    assertClassRoots(orderEntries(myModule).withoutSdk().withoutDepModules().withoutModuleSourceEntries().recursively(), getJDomJar());
    assertEnumeratorRoots(
      orderEntries(myModule).productionOnly().withoutSdk().withoutDepModules().withoutModuleSourceEntries().recursively().classes(),
      getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().withoutDepModules().withoutModuleSourceEntries());
    assertEnumeratorRoots(orderEntries(myModule).productionOnly().withoutModuleSourceEntries().withoutSdk().withoutDepModules().classes());
  }

  public void testModuleDependencyScope() throws Exception {
    final Module dep = createModule("dep");
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.TEST, true);

    assertClassRoots(orderEntries(myModule).withoutSdk());
    assertClassRoots(orderEntries(myModule).withoutSdk().recursively(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().exportedOnly().recursively(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().productionOnly().recursively());

    assertClassRoots(ProjectRootManager.getInstance(myProject).orderEntries().withoutSdk(), getJDomJar());
    assertClassRoots(ProjectRootManager.getInstance(myProject).orderEntries().withoutSdk().productionOnly(), getJDomJar());
  }

  public void testNotExportedLibrary() throws Exception {
    final Module dep = createModule("dep");
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, false);
    ModuleRootModificationUtil.addDependency(myModule, createAsmLibrary(), DependencyScope.COMPILE, false);
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, false);

    assertClassRoots(orderEntries(myModule).withoutSdk(), getAsmJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().recursively(), getAsmJar(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().recursively().exportedOnly(), getAsmJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().exportedOnly().recursively());
  }

  public void testJdkIsNotExported() throws Exception {
    assertClassRoots(orderEntries(myModule).exportedOnly());
  }

  public void testCaching() throws Exception {
    final VirtualFile[] roots = orderEntries(myModule).classes().usingCache().getRoots();
    assertOrderedEquals(roots, getRtJar());
    assertEquals(roots, orderEntries(myModule).classes().usingCache().getRoots());
    final VirtualFile[] rootsWithoutSdk = orderEntries(myModule).withoutSdk().classes().usingCache().getRoots();
    assertEmpty(rootsWithoutSdk);
    assertEquals(roots, orderEntries(myModule).classes().usingCache().getRoots());
    assertEquals(rootsWithoutSdk, orderEntries(myModule).withoutSdk().classes().usingCache().getRoots());

    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());

    assertRoots(orderEntries(myModule).classes().usingCache().getPathsList(), getRtJar(), getJDomJar());
    assertRoots(orderEntries(myModule).withoutSdk().classes().usingCache().getPathsList(), getJDomJar());
  }

  public void testCachingUrls() throws Exception {
    final String[] urls = orderEntries(myModule).classes().usingCache().getUrls();
    assertOrderedEquals(urls, getRtJar().getUrl());
    assertSame(urls, orderEntries(myModule).classes().usingCache().getUrls());

    final String[] sourceUrls = orderEntries(myModule).sources().usingCache().getUrls();
    assertEmpty(sourceUrls);
    assertSame(urls, orderEntries(myModule).classes().usingCache().getUrls());
    assertSame(sourceUrls, orderEntries(myModule).sources().usingCache().getUrls());

    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());
    assertOrderedEquals(orderEntries(myModule).classes().usingCache().getUrls(), getRtJar().getUrl(), getJDomJar().getUrl());
    assertOrderedEquals(orderEntries(myModule).sources().usingCache().getUrls(), getJDomSources().getUrl());
  }

  public void testProject() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());

    final VirtualFile srcRoot = addSourceRoot(myModule, false);
    final VirtualFile testRoot = addSourceRoot(myModule, true);
    final VirtualFile output = setModuleOutput(myModule, false);
    final VirtualFile testOutput = setModuleOutput(myModule, true);

    assertClassRoots(orderEntries(myProject).withoutSdk(), testOutput, output, getJDomJar());
    assertSourceRoots(orderEntries(myProject).withoutSdk(), srcRoot, testRoot, getJDomSources());
  }

  public void testModules() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());

    final VirtualFile srcRoot = addSourceRoot(myModule, false);
    final VirtualFile testRoot = addSourceRoot(myModule, true);
    final VirtualFile output = setModuleOutput(myModule, false);
    final VirtualFile testOutput = setModuleOutput(myModule, true);

    assertClassRoots(ProjectRootManager.getInstance(myProject).orderEntries(Arrays.asList(myModule)).withoutSdk(),
                     testOutput, output, getJDomJar());
    assertSourceRoots(ProjectRootManager.getInstance(myProject).orderEntries(Arrays.asList(myModule)).withoutSdk(),
                      srcRoot, testRoot, getJDomSources());
  }

  private static void assertClassRoots(final OrderEnumerator enumerator, VirtualFile... files) {
    assertEnumeratorRoots(enumerator.classes(), files);
  }

  private static void assertSourceRoots(final OrderEnumerator enumerator, VirtualFile... files) {
    assertEnumeratorRoots(enumerator.sources(), files);
  }

  private static void assertEnumeratorRoots(OrderRootsEnumerator rootsEnumerator, VirtualFile... files) {
    assertOrderedEquals(rootsEnumerator.getRoots(), files);
    List<String> expectedUrls = new ArrayList<String>();
    for (VirtualFile file : files) {
      expectedUrls.add(file.getUrl());
    }
    assertOrderedEquals(rootsEnumerator.getUrls(), ArrayUtil.toStringArray(expectedUrls));
  }
}
