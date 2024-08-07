/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion
import com.intellij.JavaTestUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx

import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ContentFolder
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.java.language.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
/**
 * @author peter
 */
public class HeavyNormalCompletionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testPackagePrefix() throws Throwable {
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    ApplicationManager.application.runWriteAction {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
      model.getContentEntries()[0].getSourceFolders()[0].setPackagePrefix("foo.bar.goo");
      model.commit();
    }

    myFixture.completeBasic();
    myFixture.checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo").isValid());
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo.bar").isValid());
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo.bar.goo").isValid());
  }

  public void testPreferTestCases() throws Throwable {
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    ApplicationManager.application.runWriteAction {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
      ContentEntry contentEntry = model.getContentEntries()[0];
      ContentFolder sourceFolder = contentEntry.getSourceFolders()[0];
      VirtualFile file = sourceFolder.getFile();
      contentEntry.removeSourceFolder(sourceFolder);
      contentEntry.addSourceFolder(file, true);
      model.commit();
    }

    myFixture.addClass("package foo; public class SomeTestCase {}");
    myFixture.addClass("package bar; public class SomeTestec {}");
    myFixture.addClass("package goo; public class SomeAnchor {}");

    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "SomeTestCase", "SomeAnchor", "SomeTestec");
  }

  public void testAllClassesWhenNothingIsFound() throws Throwable {
    myFixture.addClass("package foo.bar; public class AxBxCxDxEx {}");

    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testAllClassesOnSecondBasicCompletion() throws Throwable {
    myFixture.addClass("package foo.bar; public class AxBxCxDxEx {}");

    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor());
    LookupElement[] myItems = lookup.getItems().toArray(LookupElement.EMPTY_ARRAY);
    assertEquals(2, myItems.length);
    assertEquals("AxBxCxDxEx", myItems[1].getLookupString());
    assertEquals("AyByCyDyEy", myItems[0].getLookupString());
  }

  public void testMapsInvalidation() throws Exception {
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertInstanceOf(myFixture.getFile().getVirtualFile().getFileSystem(), LocalFileSystem.class); // otherwise the completion copy won't be preserved which is critical here
    myFixture.completeBasic();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "getAaa", "getBbb");
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getCaretModel().getOffset() + 2);
    assertNull(myFixture.completeBasic());
  }

  public void testQualifyInaccessibleClassName() throws Exception {
    PsiTestUtil.addModule(getProject(), "second", myFixture.getTempDirFixture().findOrCreateDir("second"));
    myFixture.addFileToProject("second/foo/bar/AxBxCxDxEx.java", "package foo.bar; class AxBxCxDxEx {}");

    myFixture.configureByText("a.java", "class Main { ABCDE<caret> }");
    myFixture.complete(CompletionType.BASIC, 3);
    myFixture.checkResult("class Main { foo.bar.AxBxCxDxEx<caret> }");
  }

  public void testPreferOwnMethods() {
    def lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/../../../lib")
    def nanoJar = lib.children.find { it.name.startsWith("nanoxml") }

    PsiTestUtil.addLibrary(myModule, 'nano1', lib.path, ["/$nanoJar.name!/"] as String[], [] as String[])

    assert JavaPsiFacade.getInstance(project).findClass('net.n3.nanoxml.StdXMLParser', GlobalSearchScope.allScope(project))

    myFixture.configureByText "a.java", """
public class Test {
  void method(net.n3.nanoxml.StdXMLParser f) {
    f.<caret>
  }
}
"""
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'getBuilder'
  }


}
