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
package com.intellij.refactoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;

import consulo.virtualFileSystem.LocalFileSystem;
import org.jetbrains.annotations.NonNls;
import com.intellij.JavaTestUtil;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.java.impl.refactoring.extractSuperclass.ExtractSuperClassProcessor;
import com.intellij.java.impl.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.java.impl.refactoring.memberPullUp.PullUpProcessor;
import consulo.ide.impl.idea.refactoring.util.DocCommentPolicy;
import com.intellij.java.impl.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import java.util.HashSet;
import consulo.util.collection.MultiMap;

/**
 * @author yole
 */
public abstract class ExtractSuperClassTest extends RefactoringTestCase {
  public void testFinalFieldInitialization() throws Exception {   // IDEADEV-19704
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("X", PsiClass.class),
           new RefactoringTestUtil.MemberDescriptor("x", PsiField.class));
  }

  public void testFieldInitializationWithCast() throws Exception {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("x", PsiField.class));
  }

   public void testMethodTypeParameter() throws Exception {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("m", PsiMethod.class));
  }

  public void testConflictUsingPrivateMethod() throws Exception {
    doTest("Test", "TestSubclass",
           new String[] {"Method <b><code>Test.foo()</code></b> is private and will not be accessible from method <b><code>x()</code></b>."},
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  public void testConflictMoveAbstractWithPrivateMethod() throws Exception {
    doTest("Test", "TestSubclass",
           new String[] {"Method <b><code>x()</code></b> uses method <b><code>Test.xx()</code></b> which won't be accessible from the subclass."},
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class, true),
           new RefactoringTestUtil.MemberDescriptor("xx", PsiMethod.class));
  }

  public void testConflictAbstractPackageLocalMethod() throws Exception {
    doTest("a.Test", "TestSubclass",
           new String[] {"Can't make method <b><code>x()</code></b> abstract as it won't be accessible from the subclass."},
           "b",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class, true));
  }

  public void testConflictUsingPackageLocalMethod() throws Exception {
    doTest("a.Test", "TestSubclass",
           new String[] {"method <b><code>Sup.foo()</code></b> won't be accessible"},
           "b",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  public void testConflictUsingPackageLocalSuperClass() throws Exception {
    doTest("a.Test", "TestSubclass",
           new String[] {"class <b><code>a.Sup</code></b> won't be accessible from package <b><code>b</code></b>"},
           "b",
           new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testNoConflictUsingProtectedMethodFromSuper() throws Exception {
    doTest("Test", "TestSubclass",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  public void testParameterNameEqualsFieldName() throws Exception {    // IDEADEV-10629
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("a", PsiField.class));
  }

  public void testExtendsLibraryClass() throws Exception {
    doTest("Test", "TestSubclass");
  }

  public void testRequiredImportRemoved() throws Exception {
    doTest("foo.impl.B", "BImpl", new RefactoringTestUtil.MemberDescriptor("getInstance", PsiMethod.class));
  }

  public void testSubstituteGenerics() throws Exception {
    doTest("B", "AB");
  }

  public void testExtendsList() throws Exception {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("List", PsiClass.class));
  }

  public void testImportsCorruption() throws Exception {
    doTest("p1.A", "AA", new RefactoringTestUtil.MemberDescriptor("m1", PsiMethod.class));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    //LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @NonNls
  private String getRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/extractSuperClass/" + getTestName(true);
  }

  private void doTest(@NonNls final String className, @NonNls final String newClassName,
                      RefactoringTestUtil.MemberDescriptor... membersToFind) throws Exception {
    doTest(className, newClassName, null, membersToFind);
  }

  private void doTest(@NonNls final String className, @NonNls final String newClassName,
                      String[] conflicts,
                      RefactoringTestUtil.MemberDescriptor... membersToFind) throws Exception {
    doTest(className, newClassName, conflicts, null, membersToFind);
  }

  private void doTest(@NonNls final String className,
                      @NonNls final String newClassName,
                      String[] conflicts,
                      String targetPackageName,
                      RefactoringTestUtil.MemberDescriptor... membersToFind) throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk14());
    final VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
    PsiClass psiClass = myJavaFacade.findClass(className, ProjectScope.getAllScope(myProject));
    assertNotNull(psiClass);
    final MemberInfo[] members = RefactoringTestUtil.findMembers(psiClass, membersToFind);
    PsiDirectory targetDirectory;
    if (targetPackageName == null) {
      targetDirectory = psiClass.getContainingFile().getContainingDirectory();
    } else {
      final PsiJavaPackage aPackage = myJavaFacade.findPackage(targetPackageName);
      assertNotNull(aPackage);
      targetDirectory = aPackage.getDirectories()[0];
    }
    ExtractSuperClassProcessor processor = new ExtractSuperClassProcessor(myProject,
                                                                          targetDirectory,
                                                                          newClassName,
                                                                          psiClass, members,
                                                                          false,
                                                                          new DocCommentPolicy<PsiComment>(DocCommentPolicy.ASIS));
    final PsiJavaPackage targetPackage;
    if (targetDirectory != null) {
      targetPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
    }
    else {
      targetPackage = null;
    }
    final PsiClass superClass = psiClass.getExtendsListTypes().length > 0 ? psiClass.getSuperClass() : null;
    final MultiMap<PsiElement, String> conflictsMap =
      PullUpConflictsUtil.checkConflicts(members, psiClass, superClass, targetPackage, targetDirectory, new InterfaceContainmentVerifier() {
        @Override
        public boolean checkedInterfacesContain(PsiMethod psiMethod) {
          return PullUpProcessor.checkedInterfacesContain(Arrays.asList(members), psiMethod);
        }
      }, false);
    if (conflicts != null) {
      if (conflictsMap.isEmpty()) {
        fail("Conflicts were not detected");
      }
      final HashSet<String> expectedConflicts = new HashSet<String>(Arrays.asList(conflicts));
      final HashSet<String> actualConflicts = new HashSet<String>(conflictsMap.values());
      assertEquals(expectedConflicts.size(), actualConflicts.size());
      for (String actualConflict : actualConflicts) {
        if (!expectedConflicts.contains(actualConflict)) {
          fail("Unexpected conflict: " + actualConflict);
        }
      }
    } else if (!conflictsMap.isEmpty()) {
      fail("Unexpected conflicts!!!");
    }
    processor.run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
  }
}
