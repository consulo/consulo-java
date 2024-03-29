/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.impl.refactoring.removemiddleman.DelegationUtils;
import com.intellij.java.impl.refactoring.removemiddleman.RemoveMiddlemanProcessor;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class RemoveMiddleManTest extends MultiFileTestCase{
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/removemiddleman/";
  }

  private void doTest(final String conflict) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.allScope(getProject()));

        if (aClass == null) aClass = myJavaFacade.findClass("p.Test", GlobalSearchScope.allScope(getProject()));
        assertNotNull("Class Test not found", aClass);

        final PsiField field = aClass.findFieldByName("myField", false);
        final Set<PsiMethod> methods = DelegationUtils.getDelegatingMethodsForField(field);
        List<MemberInfo> infos = new ArrayList<MemberInfo>();
        for (PsiMethod method : methods) {
          final MemberInfo info = new MemberInfo(method);
          info.setChecked(true);
          info.setToAbstract(true);
          infos.add(info);
        }
        try {
          RemoveMiddlemanProcessor processor = new RemoveMiddlemanProcessor(field, infos);
          processor.run();
          LocalFileSystem.getInstance().refresh(false);
          FileDocumentManager.getInstance().saveAllDocuments();
          if (conflict != null) fail("Conflict expected");
        }
        catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
          if (conflict == null) throw e;
          assertEquals(conflict, e.getMessage());
        }
      }
    });
  }

  public void testNoGetter() throws Exception {
    doTest((String)null);
  }

  public void testSiblings() throws Exception {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  
  public void testInterface() throws Exception {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  public void testPresentGetter() throws Exception {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  public void testInterfaceDelegation() throws Exception {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }
}