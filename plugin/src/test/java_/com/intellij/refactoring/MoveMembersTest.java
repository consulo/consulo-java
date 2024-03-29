package com.intellij.refactoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import com.intellij.JavaTestUtil;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.move.moveMembers.MockMoveMembersOptions;
import com.intellij.java.impl.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.java.language.util.VisibilityUtil;

public abstract class MoveMembersTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testJavadocRefs() throws Exception {
    doTest("Class1", "Class2", 0);
  }

  public void testWeirdDeclaration() throws Exception {
    doTest("A", "B", 0);
  }

  public void testInnerClass() throws Exception {
    doTest("A", "B", 0);
  }

  public void testScr11871() throws Exception {
    doTest("pack1.A", "pack1.B", 0);
  }

  public void testOuterClassTypeParameters() throws Exception {
    doTest("pack1.A", "pack2.B", 0);
  }

  public void testscr40064() throws Exception {
    doTest("Test", "Test1", 0);
  }

  public void testscr40947() throws Exception {
    doTest("A", "Test", 0, 1);
  }

  public void testIDEADEV11416() throws Exception {
    doTest("Y", "X", false, 0);
  }

  public void testDependantConstants() throws Exception {
    doTest("A", "B", 0, 1);
  }

  public void testTwoMethods() throws Exception {
    doTest("pack1.A", "pack1.C", 0, 1, 2);
  }

  public void testParameterizedRefOn() throws Exception {
    doTest("pack1.POne", "pack1.C", 1, 2);
  }

  public void testIDEADEV12448() throws Exception {
    doTest("B", "A", false, 0);
  }

  public void testFieldForwardRef() throws Exception {
    doTest("A", "Constants", 0);
  }

  public void testStaticImport() throws Exception {
    doTest("C", "B", 0);
  }

  public void testExplicitStaticImport() throws Exception {
    doTest("C", "B", 0);
  }

  public void testProtectedConstructor() throws Exception {
    doTest("pack1.A", "pack1.C", 0);
  }

  public void testUntouchedVisibility() throws Exception {
    doTest("pack1.A", "pack1.C", 0, 1);
  }

  public void testEscalateVisibility() throws Exception {
    doTest("pack1.A", "pack1.C", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }

  public void testOtherPackageImport() throws Exception {
    doTest("pack1.ClassWithStaticMethod", "pack2.OtherClass", 1);
  }

  public void testEnumConstant() throws Exception {
    doTest("B", "A", 0);
  }

  public void testEnumConstantFromCaseStatement() throws Exception {
    doTest("B", "A", 0);
  }

  public void testDependantFields() throws Exception {
    doTest("B", "A", 0);
  }

  public void testStaticImportAndOverridenMethods() throws Exception {
    doTest("bar.B", "bar.A", 0);
  }

  public void testWritableField() throws Exception {
    try {
      doTest("B", "A", 0);
      fail("conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Field <b><code>B.ONE</code></b> has write access but is moved to an interface", e.getMessage());
    }
  }

  public void testInnerToInterface() throws Exception {
    doTest("A", "B", 0);
  }

  public void testStaticToInterface() throws Exception {
    /*final LanguageLevelProjectExtension levelProjectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel level = levelProjectExtension.getLanguageLevel();
    try {
      levelProjectExtension.setLanguageLevel(LanguageLevel.JDK_1_8);
      doTest("A", "B", 0);
    }
    finally {
      levelProjectExtension.setLanguageLevel(level);
    }  */
  }
  
  public void testEscalateVisibility1() throws Exception {
    doTest("A", "B", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }
  
  public void testMultipleWithDependencies() throws Exception {
    doTest("A", "B", true, VisibilityUtil.ESCALATE_VISIBILITY, 0, 1);
  }

  public void testFromNestedToOuter() throws Exception {
    doTest("Outer.Inner", "Outer", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/moveMembers/";
  }

  private void doTest(final String sourceClassName, final String targetClassName, final int... memberIndices) throws Exception {
    doTest(sourceClassName, targetClassName, true, memberIndices);
  }

  private void doTest(final String sourceClassName,
                      final String targetClassName,
                      final boolean lowercaseFirstLetter,
                      final int... memberIndices) throws Exception {
    doTest(sourceClassName, targetClassName, lowercaseFirstLetter, null, memberIndices);
  }

  private void doTest(final String sourceClassName,
                      final String targetClassName,
                      final boolean lowercaseFirstLetter,
                      final String defaultVisibility,
                      final int... memberIndices)
    throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        MoveMembersTest.this.performAction(sourceClassName, targetClassName, memberIndices, defaultVisibility);
      }
    }, lowercaseFirstLetter);
  }

  private void performAction(String sourceClassName, String targetClassName, int[] memberIndices, final String visibility) throws Exception {
    PsiClass sourceClass = myJavaFacade.findClass(sourceClassName, ProjectScope.getProjectScope(myProject));
    assertNotNull("Class " + sourceClassName + " not found", sourceClass);
    PsiClass targetClass = myJavaFacade.findClass(targetClassName, ProjectScope.getProjectScope(myProject));
    assertNotNull("Class " + targetClassName + " not found", targetClass);

    PsiElement[] children = sourceClass.getChildren();
    ArrayList<PsiMember> members = new ArrayList<PsiMember>();
    for (PsiElement child : children) {
      if (child instanceof PsiMember) {
        members.add(((PsiMember) child));
      }
    }

    LinkedHashSet<PsiMember> memberSet = new LinkedHashSet<PsiMember>();
    for (int index : memberIndices) {
      PsiMember member = members.get(index);
      assertTrue(member.hasModifierProperty(PsiModifier.STATIC));
      memberSet.add(member);
    }

    MockMoveMembersOptions options = new MockMoveMembersOptions(targetClass.getQualifiedName(), memberSet);
    options.setMemberVisibility(visibility);
    new MoveMembersProcessor(myProject, null, options).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
