/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import consulo.document.FileDocumentManager;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.impl.refactoring.extractclass.ExtractClassProcessor;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;

public abstract class ExtractClassTest extends MultiFileTestCase{
  @Override
  protected String getTestRoot() {
    return "/refactoring/extractClass/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTestMethod() throws Exception {
    doTestMethod(null);
  }

  private void doTestMethod(String conflicts) throws Exception {
    doTestMethod("foo", conflicts);
  }

  private void doTestMethod(String methodName, String conflicts) throws Exception {
    doTestMethod(methodName, conflicts, "Test");
  }

  private void doTestMethod(final String methodName,
                            final String conflicts,
                            final String qualifiedName) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass(qualifiedName, GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
        methods.add(aClass.findMethodsByName(methodName, false)[0]);
        
        doTest(aClass, methods, new ArrayList<PsiField>(), conflicts, false);
      }
    });
  }

  public void testStatic() throws Exception {
    doTestMethod();
  }

  public void testStaticImport() throws Exception {
    doTestMethod();
  }

  public void testFieldReference() throws Exception {
    doTestMethod("foo", "Field 'myField' needs getter");
  }

  public void testVarargs() throws Exception {
    doTestMethod();
  }

  public void testNoDelegation() throws Exception {
    doTestMethod();
  }

  public void testNoFieldDelegation() throws Exception {
    doTestFieldAndMethod();
  }

  public void testFieldInitializers() throws Exception {
    doTestField(null);
  }

  public void testDependantFieldInitializers() throws Exception {
    doTestField(null);
  }

  public void testDependantNonStaticFieldInitializers() throws Exception {
    doTestField(null, true);
  }

  public void testInheritanceDelegation() throws Exception {
    doTestMethod();
  }

  public void testEnumSwitch() throws Exception {
    doTestMethod();
  }

  public void testImplicitReferenceTypeParameters() throws Exception {
    doTestMethod();
  }

  public void testStaticImports() throws Exception {
    doTestMethod("foo", null, "foo.Test");
  }

  public void testNoConstructorParams() throws Exception {
    doTestFieldAndMethod();
  }

  public void testConstructorParams() throws Exception {
    doTestFieldAndMethod();
  }

  private void doTestFieldAndMethod() throws Exception {
    doTestFieldAndMethod("bar");
  }

  public void testInnerClassRefs() throws Exception {
    doTestInnerClass();
  }

  private void doTestFieldAndMethod(final String methodName) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
        methods.add(aClass.findMethodsByName(methodName, false)[0]);

        ArrayList<PsiField> fields = new ArrayList<PsiField>();
        fields.add(aClass.findFieldByName("myT", false));

        doTest(aClass, methods, fields, null, false);
      }
    });
  }

  private void doTestField(String conflicts) throws Exception {
    doTestField(conflicts, false);
  }

  private void doTestField(final String conflicts, final boolean generateGettersSetters) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();

        ArrayList<PsiField> fields = new ArrayList<PsiField>();
        fields.add(aClass.findFieldByName("myT", false));

        doTest(aClass, methods, fields, conflicts, generateGettersSetters);
      }
    });
  }

  private static void doTest(PsiClass aClass, ArrayList<PsiMethod> methods, ArrayList<PsiField> fields, String conflicts,
                             boolean generateGettersSetters) {
    try {
      ExtractClassProcessor processor = new ExtractClassProcessor(aClass, fields, methods, new ArrayList<PsiClass>(), StringUtil.getPackageName(aClass.getQualifiedName()), null,
                                                                  "Extracted", null, generateGettersSetters, Collections.<MemberInfo>emptyList());
      processor.run();
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflicts != null) {
        TreeSet expectedConflictsSet = new TreeSet(Arrays.asList(conflicts.split("\n")));
        TreeSet actualConflictsSet = new TreeSet(Arrays.asList(e.getMessage().split("\n")));
        Assert.assertEquals(expectedConflictsSet, actualConflictsSet);
        return;
      } else {
        fail(e.getMessage());
      }
    }
    if (conflicts != null) {
      fail("Conflicts were not detected: " + conflicts);
    }
  }

  public void testGenerateGetters() throws Exception {
    doTestField(null, true);
  }

  public void testIncrementDecrement() throws Exception {
    doTestField(null, true);
  }


  public void testGetters() throws Exception {
    doTestFieldAndMethod("getMyT");
  }

  public void testHierarchy() throws Exception {
    doTestFieldAndMethod();
  }

  public void testPublicFieldDelegation() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        ArrayList<PsiField> fields = new ArrayList<PsiField>();
        fields.add(aClass.findFieldByName("myT", false));

        ExtractClassProcessor processor = new ExtractClassProcessor(aClass, fields, new ArrayList<PsiMethod>(), new ArrayList<PsiClass>(), "", "Extracted");
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  private void doTestInnerClass() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
        classes.add(aClass.findInnerClassByName("Inner", false));
        ExtractClassProcessor processor = new ExtractClassProcessor(aClass, new ArrayList<PsiField>(), new ArrayList<PsiMethod>(), classes, "", "Extracted");
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testInner() throws Exception {
    doTestInnerClass();
  }

  public void testMultipleGetters() throws Exception {
    doTestField("Field 'myT' needs getter");
  }

  public void testMultipleGetters1() throws Exception {
    doTestMethod("getMyT", "Field 'myT' needs getter");
  }

  public void testUsedInInitializer() throws Exception {
    doTestField("Field 'myT' needs setter\n" +
                "Field 'myT' needs getter\n" +
                "Class initializer requires moved members");
  }

  public void testUsedInConstructor() throws Exception {
    doTestField("Field 'myT' needs getter\n" +
                "Field 'myT' needs setter\n" +
                "Constructor requires moved members");
  }

  public void testRefInJavadoc() throws Exception {
    doTestField(null);
  }

  public void testMethodTypeParameters() throws Exception {
    doTestMethod();
  }

  public void testPublicVisibility() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
        methods.add(aClass.findMethodsByName("foos", false)[0]);

        ArrayList<PsiField> fields = new ArrayList<PsiField>();
        fields.add(aClass.findFieldByName("myT", false));

        ExtractClassProcessor processor =
          new ExtractClassProcessor(aClass, fields, methods, new ArrayList<PsiClass>(), "", null, "Extracted", PsiModifier.PUBLIC, false, Collections.<MemberInfo>emptyList());
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }
}