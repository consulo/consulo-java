package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import consulo.component.extension.Extensions;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.language.editor.refactoring.rename.AutomaticRenamerFactory;
import org.jetbrains.annotations.NonNls;

public abstract class RenameClassTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testNonJava() throws Exception {
    doTest("pack1.Class1", "Class1New");
  }

  public void testCollision() throws Exception {
    doTest("pack1.MyList", "List");
  }

  public void testInnerClass() throws Exception {
    doTest("pack1.OuterClass.InnerClass", "NewInnerClass");
  }

  public void testImport() throws Exception {
    doTest("a.Blubfoo", "BlubFoo");
  }

  public void testInSameFile() throws Exception {
    doTest("Two", "Object");
  }
  
  public void testConstructorJavadoc() throws Exception {
    doTest("Test", "Test1");
  }

  public void testCollision1() throws Exception {
    doTest("Loader", "Reader");
  }

  public void testImplicitReferenceToDefaultCtr() throws Exception {
    doTest("pack1.Parent", "ParentXXX");
  }

  public void testImplicitlyImported() throws Exception {
    doTest("pack1.A", "Object");
  }

  public void testAutomaticRenameVars() throws Exception {
    doRenameClass("XX", "Y");
  }

  private void doRenameClass(final String className, final String newName) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(getProject()));
        assertNotNull("Class XX not found", aClass);

        final RenameProcessor processor = new RenameProcessor(myProject, aClass, newName, true, true);
        for (AutomaticRenamerFactory factory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
          processor.addRenamerFactory(factory);
        }
        processor.run();
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testAutomaticRenameInheritors() throws Exception {
    doRenameClass("MyClass", "MyClass1");
  }

  public void testAutomaticRenameVarsCollision() throws Exception {
    doTest("XX", "Y");
  }

  private void doTest(@NonNls final String qClassName, @NonNls final String newName) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        RenameClassTest.this.performAction(qClassName, newName);
      }
    });
  }

  private void performAction(String qClassName, String newName) throws Exception {
    PsiClass aClass = myJavaFacade.findClass(qClassName, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Class " + qClassName + " not found", aClass);

    new RenameProcessor(myProject, aClass, newName, true, true).run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/renameClass/";
  }
}
