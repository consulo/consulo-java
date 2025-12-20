package com.intellij.codeInsight.intention;

import com.intellij.java.impl.codeInsight.intention.impl.CreateSubclassAction;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiDirectory;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.MultiFileTestCase;

/**
 * @author yole
 */
public abstract class CreateSubclassTest extends MultiFileTestCase {
  public void testGenerics() throws Exception {
    doTest();
  }

  public void testInnerClassImplement() throws Exception {
    doTestInner();
  }

  public void testInnerClass() throws Exception {
    doTestInner();
  }

  private void doTestInner() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass superClass = myJavaFacade.findClass("Test", ProjectScope.getAllScope(myProject));
        assertNotNull(superClass);
        PsiClass inner = superClass.findInnerClassByName("Inner", false);
        assertNotNull(inner);
        CreateSubclassAction.createInnerClass(inner);
      }
    });
  }

  private void doTest() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiDirectory root = myPsiManager.findDirectory(rootDir);
        PsiClass superClass = myJavaFacade.findClass("Superclass", ProjectScope.getAllScope(myProject));
        CreateSubclassAction.createSubclass(superClass, root, "Subclass");
      }
    });
  }

  @Override
  protected String getTestRoot() {
    return "/codeInsight/createSubclass/";
  }
}
