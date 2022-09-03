package com.intellij.ide.hierarchy;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.ide.hierarchy.call.CallerMethodsTreeStructure;
import consulo.application.util.function.Computable;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestBase;

/**
 * @author yole
 */
public abstract class JavaCallHierarchyTest extends HierarchyViewTestBase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getBasePath() {
    return "ide/hierarchy/call/" + getTestName(false);
  }

  private void doJavaCallTypeHierarchyTest(final String classFqn, final String methodName, final String... fileNames) throws Exception {
    doHierarchyTest(new Computable<HierarchyTreeStructure>() {
      @Override
      public HierarchyTreeStructure compute() {
        final PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(classFqn, ProjectScope.getProjectScope(getProject()));
        final PsiMethod method = psiClass.findMethodsByName(methodName, false) [0];
        return new CallerMethodsTreeStructure(getProject(), method, HierarchyBrowserBaseEx.SCOPE_PROJECT);
      }
    }, fileNames);
  }

  public void testIdeaDev41005() throws Exception {
    doJavaCallTypeHierarchyTest("B", "xyzzy", "B.java", "D.java", "A.java");
  }

  public void testIdeaDev41005_Inheritance() throws Exception {
    doJavaCallTypeHierarchyTest("D", "xyzzy", "B.java", "D.java", "A.java", "C.java");
  }

  public void testIdeaDev41005_Sibling() throws Exception {
    doJavaCallTypeHierarchyTest("D", "xyzzy", "B.java", "D.java", "A.java", "C.java");
  }

  public void testIdeaDev41005_SiblingUnderInheritance() throws Exception {
    doJavaCallTypeHierarchyTest("D", "xyzzy", "B.java", "D.java", "A.java", "C.java", "CChild.java");
  }

  public void testIdeaDev41232() throws Exception {
    doJavaCallTypeHierarchyTest("A", "main", "B.java", "A.java");
  }
}
