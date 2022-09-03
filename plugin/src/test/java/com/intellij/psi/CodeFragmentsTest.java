package com.intellij.psi;

import com.intellij.java.language.psi.JavaCodeFragmentFactory;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestCase;
import consulo.language.psi.scope.GlobalSearchScope;

@PlatformTestCase.WrapInCommand
public abstract class CodeFragmentsTest extends PsiTestCase{
  public void testAddImport() throws Exception {
    PsiCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myProject).createExpressionCodeFragment("AAA.foo()", null, null, false);
    PsiClass arrayListClass = myJavaFacade.findClass("java.util.ArrayList", GlobalSearchScope.allScope(getProject()));
    PsiReference ref = fragment.findReferenceAt(0);
    ref.bindToElement(arrayListClass);
    assertEquals("ArrayList.foo()", fragment.getText());
  }
}
