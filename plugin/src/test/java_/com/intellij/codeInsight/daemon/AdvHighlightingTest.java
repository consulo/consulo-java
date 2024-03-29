/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.java.impl.analysis.PackagesScopesProvider;
import consulo.ide.impl.idea.application.options.colors.ScopeAttributesUtil;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.TextAttributesKey;
import consulo.colorScheme.EffectType;
import consulo.colorScheme.TextAttributes;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.idea.openapi.roots.ModuleRootModificationUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.ide.impl.idea.packageDependencies.DependencyValidationManager;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.content.scope.NamedScope;
import consulo.language.editor.scope.NamedScopeManager;
import consulo.content.scope.NamedScopesHolder;
import com.intellij.java.impl.psi.search.scope.packageSet.PatternPackageSet;
import com.intellij.testFramework.IdeaTestUtil;
import consulo.ui.style.StandardColors;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.io.File;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * This class intended for "heavily-loaded" tests only, e.g. those need to setup separate project directory structure to run.
 * For "lightweight" tests use LightAdvHighlightingTest.
 */
public abstract class AdvHighlightingTest extends DaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting";

  @Override
  protected Sdk getTestProjectJdk() {
    //LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_4);
    return IdeaTestUtil.getMockJdk14();
  }

  public void testPackageLocals() throws Exception {
    doTest(BASE_PATH + "/packageLocals/x/sub/UsingMain.java", BASE_PATH + "/packageLocals", false, false);
  }

  public void testPackageLocalClassInTheMiddle() throws Exception {
    doTest(BASE_PATH + "/packageLocals/x/A.java", BASE_PATH + "/packageLocals", false, false);
  }

  public void testEffectiveAccessLevel() throws Exception {
    doTest(BASE_PATH + "/accessLevel/effectiveAccess/p2/p3.java", BASE_PATH + "/accessLevel", false, false);
  }

  public void testSingleImportConflict() throws Exception {
    doTest(BASE_PATH + "/singleImport/d.java", BASE_PATH + "/singleImport", false, false);
  }

  public void testDuplicateTopLevelClass() throws Exception {
    doTest(BASE_PATH + "/duplicateClass/A.java", BASE_PATH + "/duplicateClass", false, false);
  }

  public void testDuplicateTopLevelClass2() throws Exception {
    doTest(BASE_PATH + "/duplicateClass/java/lang/Runnable.java", BASE_PATH + "/duplicateClass", false, false);
  }

  public void testProtectedConstructorCall() throws Exception {
    doTest(BASE_PATH + "/protectedConstructor/p2/C2.java", BASE_PATH + "/protectedConstructor", false, false);
  }

  public void testProtectedConstructorCallInSamePackage() throws Exception {
    doTest(BASE_PATH + "/protectedConstructor/samePackage/C2.java", BASE_PATH + "/protectedConstructor", false, false);
  }

  public void testProtectedConstructorCallInInner() throws Exception {
    doTest(BASE_PATH + "/protectedConstructorInInner/p2/C2.java", BASE_PATH + "/protectedConstructorInInner", false, false);
  }

  public void testArrayLengthAccessFromSubClass() throws Exception {
    doTest(BASE_PATH + "/arrayLength/p2/SubTest.java", BASE_PATH + "/arrayLength", false, false);
  }

  public void testAccessibleMember() throws Exception {
    doTest(BASE_PATH + "/accessibleMember/com/red/C.java", BASE_PATH + "/accessibleMember", false, false);
  }

  public void testStaticPackageLocalMember() throws Exception {
    doTest(BASE_PATH + "/staticPackageLocalMember/p1/C.java", BASE_PATH + "/staticPackageLocalMember", false, false);
  }

  public void testOnDemandImportConflict() throws Exception {
    doTest(BASE_PATH + "/onDemandImportConflict/Outer.java", BASE_PATH + "/onDemandImportConflict", false, false);
  }

  public void testPackageLocalOverride() throws Exception {
    doTest(BASE_PATH + "/packageLocalOverride/y/C.java", BASE_PATH + "/packageLocalOverride", false, false);
  }

  public void testPackageLocalOverrideJustCheckThatPackageLocalMethodDoesNotGetOverridden() throws Exception {
    doTest(BASE_PATH + "/packageLocalOverride/y/B.java", BASE_PATH + "/packageLocalOverride", false, false);
  }

  public void testProtectedAccessFromOtherPackage() throws Exception {
    doTest(BASE_PATH + "/protectedAccessFromOtherPackage/a/Main.java", BASE_PATH + "/protectedAccessFromOtherPackage", false, false);
  }

  public void testProtectedFieldAccessFromOtherPackage() throws Exception {
    doTest(BASE_PATH + "/protectedAccessFromOtherPackage/a/A.java", BASE_PATH + "/protectedAccessFromOtherPackage", false, false);
  }

  public void testPackageLocalClassInTheMiddle1() throws Exception {
    doTest(BASE_PATH + "/foreignPackageInBetween/a/A1.java", BASE_PATH + "/foreignPackageInBetween", false, false);
  }

  public void testImportOnDemand() throws Exception {
    doTest(BASE_PATH + "/importOnDemand/y/Y.java", BASE_PATH + "/importOnDemand", false, false);
  }

  public void testImportOnDemandVsSingle() throws Exception {
    doTest(BASE_PATH + "/importOnDemandVsSingle/y/Y.java", BASE_PATH + "/importOnDemandVsSingle", false, false);
  }

  public void testImportSingleVsSamePackage() throws Exception {
    doTest(BASE_PATH + "/importSingleVsSamePackage/y/Y.java", BASE_PATH + "/importSingleVsSamePackage", false, false);
  }

  public void testImportSingleVsInherited() throws Exception {
    doTest(BASE_PATH + "/importSingleVsInherited/Test.java", BASE_PATH + "/importSingleVsInherited", false, false);
  }

  public void testImportOnDemandVsInherited() throws Exception {
    doTest(BASE_PATH + "/importOnDemandVsInherited/Test.java", BASE_PATH + "/importOnDemandVsInherited", false, false);
  }

  public void testOverridePackageLocal() throws Exception {
    doTest(BASE_PATH + "/overridePackageLocal/x/y/Derived.java", BASE_PATH + "/overridePackageLocal", false, false);
  }

  public void testAlreadyImportedClass() throws Exception {
    doTest(BASE_PATH + "/alreadyImportedClass/pack/AlreadyImportedClass.java", BASE_PATH + "/alreadyImportedClass", false, false);
  }

  public void testImportDefaultPackage1() throws Exception {
    doTest(BASE_PATH + "/importDefaultPackage/x/Usage.java", BASE_PATH + "/importDefaultPackage", false, false);
  }

  public void testImportDefaultPackage2() throws Exception {
    doTest(BASE_PATH + "/importDefaultPackage/x/ImportOnDemandUsage.java", BASE_PATH + "/importDefaultPackage", false, false);
  }

  public void testImportDefaultPackage3() throws Exception {
    doTest(BASE_PATH + "/importDefaultPackage/Test.java", BASE_PATH + "/importDefaultPackage", false, false);
  }

  public void testImportDefaultPackageInvalid() throws Exception {
    doTest(BASE_PATH + "/importDefaultPackage/x/InvalidUse.java", BASE_PATH + "/importDefaultPackage", false, false);
  }

  public void testScopeBased() throws Exception {
    NamedScope xScope = new NamedScope("xxx", new PatternPackageSet("x..*", PatternPackageSet.SCOPE_SOURCE, null));
    NamedScope utilScope = new NamedScope("util", new PatternPackageSet("java.util.*", PatternPackageSet.SCOPE_LIBRARY, null));
    NamedScopeManager scopeManager = NamedScopeManager.getInstance(getProject());
    scopeManager.addScope(xScope);
    scopeManager.addScope(utilScope);

    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);
    TextAttributesKey xKey = ScopeAttributesUtil.getScopeTextAttributeKey(xScope.getName());
    TextAttributes xAttributes = new TextAttributes(StandardColors.CYAN, StandardColors.GRAY, StandardColors.BLUE, EffectType.BOXED, Font.ITALIC);
    scheme.setAttributes(xKey, xAttributes);

    TextAttributesKey utilKey = ScopeAttributesUtil.getScopeTextAttributeKey(utilScope.getName());
    TextAttributes utilAttributes = new TextAttributes(StandardColors.GRAY, StandardColors.MAGENTA, StandardColors.ORANGE, EffectType.STRIKEOUT, Font.BOLD);
    scheme.setAttributes(utilKey, utilAttributes);

    try {
      testFile(BASE_PATH + "/scopeBased/x/X.java").projectRoot(BASE_PATH + "/scopeBased").checkSymbolNames().test();
    }
    finally {
      scopeManager.removeAllSets();
    }
  }

  public void testSharedScopeBased() throws Exception {
    NamedScope xScope = new NamedScope("xxx", new PatternPackageSet("x..*", PatternPackageSet.SCOPE_ANY, null));
    NamedScope utilScope = new NamedScope("util", new PatternPackageSet("java.util.*", PatternPackageSet.SCOPE_LIBRARY, null));
    NamedScopesHolder scopeManager = DependencyValidationManager.getInstance(getProject());
    scopeManager.addScope(xScope);
    scopeManager.addScope(utilScope);

    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);
    TextAttributesKey xKey = ScopeAttributesUtil.getScopeTextAttributeKey(xScope.getName());
    TextAttributes xAttributes = new TextAttributes(StandardColors.CYAN, StandardColors.GRAY, StandardColors.BLUE, null, Font.ITALIC);
    scheme.setAttributes(xKey, xAttributes);

    TextAttributesKey utilKey = ScopeAttributesUtil.getScopeTextAttributeKey(utilScope.getName());
    TextAttributes utilAttributes = new TextAttributes(StandardColors.GRAY, StandardColors.MAGENTA, StandardColors.ORANGE, EffectType.STRIKEOUT, Font.BOLD);
    scheme.setAttributes(utilKey, utilAttributes);

    NamedScope projectScope = PackagesScopesProvider.getInstance(myProject).getProjectProductionScope();
    TextAttributesKey projectKey = ScopeAttributesUtil.getScopeTextAttributeKey(projectScope.getName());
    TextAttributes projectAttributes = new TextAttributes(null, null, StandardColors.BLUE, EffectType.BOXED, Font.ITALIC);
    scheme.setAttributes(projectKey, projectAttributes);

    try {
      testFile(BASE_PATH + "/scopeBased/x/Shared.java").projectRoot(BASE_PATH + "/scopeBased").checkSymbolNames().test();
    }
    finally {
      scopeManager.removeAllSets();
    }
  }

  public void testMultiJDKConflict() throws Exception {
    String path = BASE_PATH + "/" + getTestName(true);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    assert root != null : path;
    loadAllModulesUnder(root);

    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    Module java4 = moduleManager.findModuleByName("java4");
    Module java5 = moduleManager.findModuleByName("java5");
    //setJdk(java4, IdeaTestUtil.getMockJdk17("java 1.4"));
    //setJdk(java5, IdeaTestUtil.getMockJdk17("java 1.5"));
    ModuleRootModificationUtil.addDependency(java5, java4);

    configureByExistingFile(root.findFileByRelativePath("moduleJava5/com/Java5.java"));
    Collection<HighlightInfo> infos = highlightErrors();
    assertEmpty(infos);
  }

  public void testSameFQNClasses() throws Exception {
    String path = BASE_PATH + "/" + getTestName(true);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    assert root != null : path;
    loadAllModulesUnder(root);

    configureByExistingFile(root.findFileByRelativePath("client/src/BugTest.java"));
    Collection<HighlightInfo> infos = highlightErrors();
    assertEmpty(infos);
  }

  public void testSameClassesInSourceAndLib() throws Exception {
    String path = BASE_PATH + "/" + getTestName(true);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    assert root != null : path;
    loadAllModulesUnder(root);

    configureByExistingFile(root.findFileByRelativePath("src/ppp/SomeClass.java"));
    PsiField field = ((PsiJavaFile)myFile).getClasses()[0].findFieldByName("f", false);
    assert field != null;
    PsiClass aClass = ((PsiClassType)field.getType()).resolve();
    assert aClass != null;
    assertEquals("ppp.BadClass", aClass.getQualifiedName());
    //lies in source
    VirtualFile vFile1 = myFile.getVirtualFile();
    VirtualFile vFile2 = aClass.getContainingFile().getVirtualFile();
    assert vFile1 != null;
    assert vFile2 != null;
    assertEquals(vFile1.getParent(), vFile2.getParent());
  }

  public void testNotAKeywords() throws Exception {
    //LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_4);
    doTest(BASE_PATH + "/notAKeywords/Test.java", BASE_PATH + "/notAKeywords", false, false);
  }

  public void testPackageAndClassConflict11() throws Exception {
    doTest(BASE_PATH + "/packageClassClash1/pkg/sub/Test.java", BASE_PATH + "/packageClassClash1", false, false);
  }

  public void testPackageAndClassConflict12() throws Exception {
    doTest(BASE_PATH + "/packageClassClash1/pkg/sub.java", BASE_PATH + "/packageClassClash1", false, false);
  }

  public void testPackageAndClassConflict21() throws Exception {
    doTest(BASE_PATH + "/packageClassClash2/pkg/sub/Test.java", BASE_PATH + "/packageClassClash2", false, false);
  }

  public void testPackageAndClassConflict22() throws Exception {
    doTest(BASE_PATH + "/packageClassClash2/pkg/Sub.java", BASE_PATH + "/packageClassClash2", false, false);
  }

  // todo[r.sh] IDEA-91596 (probably PJCRE.resolve() should be changed to qualifier-first model)
  //public void testPackageAndClassConflict3() throws Exception {
  //  doTest(BASE_PATH + "/packageClassClash3/test/Test.java", BASE_PATH + "/packageClassClash3", false, false);
  //}

  public void testDefaultPackageAndClassConflict() throws Exception {
    doTest(BASE_PATH + "/lang.java", false, false);
  }

  public void testPackageObscuring() throws Exception {
    doTest(BASE_PATH + "/packageObscuring/main/Main.java", BASE_PATH + "/packageObscuring", false, false);
  }
}
