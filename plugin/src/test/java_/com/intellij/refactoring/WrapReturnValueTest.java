/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.impl.refactoring.wrapreturnvalue.WrapReturnValueProcessor;
import com.intellij.JavaTestUtil;
import org.jetbrains.annotations.NonNls;

public abstract class WrapReturnValueTest extends MultiFileTestCase{
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/wrapReturnValue/";
  }

  private void doTest(boolean existing) throws Exception {
    doTest(existing, null);
  }

  private void doTest(boolean existing, @NonNls String exceptionMessage) throws Exception {
    doTest(existing, exceptionMessage, false);
  }

  private void doTest(final boolean existing, String exceptionMessage, final boolean createInnerClass) throws Exception {
    try {
      doTest(new PerformAction() {
        @Override
        public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
          PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(getProject()));

          assertNotNull("Class Test not found", aClass);

          PsiMethod method = aClass.findMethodsByName("foo", false)[0];



          @NonNls String wrapperClassName = "Wrapper";

          PsiClass wrapperClass = myJavaFacade.findClass(wrapperClassName, GlobalSearchScope.projectScope(getProject()));

          assertTrue(!existing || wrapperClass != null);
          PsiField delegateField = existing ? wrapperClass.findFieldByName("myField", false) : null;
          WrapReturnValueProcessor processor = new WrapReturnValueProcessor(wrapperClassName, "",
                                                                            null, method, existing, createInnerClass,
                                                                            delegateField);
          processor.run();
          /*LocalFileSystem.getInstance().refresh(false);
          FileDocumentManager.getInstance().saveAllDocuments();*/
        }
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (exceptionMessage != null) {
        assertEquals(exceptionMessage, e.getMessage());
        return;
      }
      throw e;
    }
    if (exceptionMessage != null) {
      fail("Conflict was not found");
    }
  }

  public void testSimple() throws Exception {
    doTest(false);
  }

  public void testGenerics() throws Exception {
    doTest(false);
  }

  public void testInconsistentWrapper() throws Exception {
    doTest(true, "Existing class does not have getter for selected field");
  }

  public void testWrapper() throws Exception {
    doTest(true);
  }

  public void testStrip() throws Exception {
    doTest(true);
  }

  public void testNoConstructor() throws Exception {
    doTest(true, "Existing class does not have appropriate constructor");
  }

  public void testInnerClass() throws Exception {
    doTest(false, null, true);
  }

  public void testHierarchy() throws Exception {
    doTest(false, null, true);
  }

  public void testAnonymous() throws Exception {
    doTest(true, null, false);
  }

  public void testWrongFieldAssignment() throws Exception {
    doTest(true, "Existing class does not have appropriate constructor", false);
  }

  public void testWrongFieldType() throws Exception {
    doTest(true, "Existing class does not have appropriate constructor", false);
  }

  public void testStaticMethodInnerClass() throws Exception {
    doTest(false, null, true);
  }

  public void testRawReturnType() throws Exception {
    doTest(true, "Existing class does not have appropriate constructor");
  }

  public void testReturnInsideLambda() throws Exception {
    doTest(false, null, true);
  }
  
}