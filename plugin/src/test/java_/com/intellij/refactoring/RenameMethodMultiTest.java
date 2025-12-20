package com.intellij.refactoring;

import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import com.intellij.JavaTestUtil;
import org.junit.Assert;

/**
 * @author dsl
 */
public abstract class RenameMethodMultiTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/renameMethod/multi/";
  }

  public void testStaticImport1() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport2() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport3() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport4() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testDefaultAnnotationMethod() throws Exception {
    doTest("pack1.A", "int value()", "intValue");
  }

  public void testRename2OverrideFinal() throws Exception {
    try {
      doTest("p.B", "void method()", "finalMethod");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("Renaming method will override final \"method <b><code>A.finalMethod()</code></b>\"\n" +
                          "Method finalMethod() will override \n" +
                          "a method of the base class <b><code>p.A</code></b>.", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testRename2HideFromAnonymous() throws Exception {
    doTest("p.Foo", "void buzz(int i)", "bazz");
  }

  public void testAlignedMultilineParameters() throws Exception {
    getCurrentCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCurrentCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest("void test123(int i, int j)", "test123asd");
  }

  private void doTest(String methodSignature, String newName) throws Exception {
    doTest(getTestName(false), methodSignature, newName);
  }

  private void doTest(final String className, final String methodSignature, final String newName) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        JavaPsiFacade manager = getJavaFacade();
        PsiClass aClass = manager.findClass(className, GlobalSearchScope.moduleScope(myModule));
        assertNotNull(aClass);
        PsiMethod methodBySignature = aClass.findMethodBySignature(manager.getElementFactory().createMethodFromText(
                  methodSignature + "{}", null), false);
        assertNotNull(methodBySignature);
        RenameProcessor renameProcessor = new RenameProcessor(myProject, methodBySignature, newName, false, false);
        renameProcessor.run();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

}
