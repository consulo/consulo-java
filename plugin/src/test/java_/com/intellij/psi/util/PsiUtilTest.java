package com.intellij.psi.util;

import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.testFramework.LightCodeInsightTestCase;
import consulo.language.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 *  @author dsl
 */
public abstract class PsiUtilTest extends LightCodeInsightTestCase {
  public void testTypeParameterIterator() throws Exception {
    PsiClass classA = createClass("class A<T> {}");
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA);
    compareIterator(new String[]{"T"}, iterator);
  }

  private static PsiClass createClass(String text) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(ourProject).getElementFactory();
    final PsiClass classA = factory.createClassFromText(text, null).getInnerClasses()[0];
    return classA;
  }

  public void testTypeParameterIterator1() throws Exception {
    final PsiClass classA = createClass("class A<T> { class B<X> {}}");
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA.getInnerClasses()[0]);
    compareIterator(new String[]{"X","T"}, iterator);
  }

  public void testTypeParameterIterator2() throws Exception {
    final PsiClass classA = createClass("class A<T> { static class B<X> {}}");
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA.getInnerClasses()[0]);
    compareIterator(new String[]{"X"}, iterator);
  }

  public void testTypeParameterIterator3() throws Exception {
    final PsiClass classA = createClass("class A<T> { class B<X, Y> {}}");
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(classA.getInnerClasses()[0]);
    compareIterator(new String[]{"Y", "X", "T"}, iterator);
  }


  private static void compareIterator(String[] expected, Iterator<PsiTypeParameter> it) {
    final ArrayList<String> actual = new ArrayList<String>();
    while (it.hasNext()) {
      PsiTypeParameter typeParameter = it.next();
      actual.add(typeParameter.getName());
    }
    assertEquals(Arrays.asList(expected).toString(), actual.toString());
  }
}
