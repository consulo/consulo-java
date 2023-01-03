package com.intellij.find.findUsages;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import com.intellij.testFramework.PsiTestCase;
import junit.framework.Assert;

/**
 *  @author dsl
 */
public abstract class FindParameterTest extends PsiTestCase {
  public void testMethod() throws Exception {
    String text =
            "void method(final int i) {" +
            "  Runnable runnable = new Runnable() {" +
            "    public void run() {" +
            "      System.out.println(i);" +
            "    }" +
            "  };" +
            "  System.out.println(i);" +
            "}";
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    final PsiMethod methodFromText = elementFactory.createMethodFromText(text, null);
    final PsiParameter[] parameters = methodFromText.getParameterList().getParameters();
    final PsiReference[] references =
      ReferencesSearch.search(parameters[0], new LocalSearchScope(methodFromText), false).toArray(new PsiReference[0]);
    Assert.assertEquals(references.length, 2);
  }
}
