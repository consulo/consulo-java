package com.intellij.codeInsight.daemon;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.Assert;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import consulo.codeInsight.TargetElementUtil;

/**
 * User: anna
 */
public abstract class ImplementationsViewTest extends LightCodeInsightFixtureTestCase {
  public void testFromCompletion() {
    myFixture.configureByText("a.java", "public class Foo {\n" +
                                        "    private final String text;\n" +
                                        "\n" +
                                        "    public Foo(String text) {\n" +
                                        "//        this.text = text;\n" +
                                        "    }\n" +
                                        "\n" +
                                        "    public Foo(int i) {\n" +
                                        "    }\n" +
                                        "\n" +
                                        "    public static void main(String[] args) {\n" +
                                        "        final Foo foo = new Foo(\"\");\n" +
                                        "        foo.to<caret>\n" +
                                        "    }\n" +
                                        "\n" +
                                        "    @Override\n" +
                                        "    public String toString() {\n" +
                                        "        return \"text\";\n" +
                                        "    }\n" +
                                        "    public void totttt(){}" +
                                        "}");
    myFixture.completeBasic();

    PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getAllAccepted());

    assert element != null;
    final String newText = ImplementationViewComponent.getNewText(element);

    assertEquals("    @Override\n" +
                 "    public String toString() {\n" +
                 "        return \"text\";\n" +
                 "    }", newText);
  }

  public void testFromEditor() {
    myFixture.configureByText("a.java", "public class Foo {\n" +
                                        "    private final String text;\n" +
                                        "\n" +
                                        "    public Foo(String text) {\n" +
                                        "//        this.text = text;\n" +
                                        "    }\n" +
                                        "\n" +
                                        "    public Foo(int i) {\n" +
                                        "    }\n" +
                                        "\n" +
                                        "    @Override\n" +
                                        "    public String to<caret>String() {\n" +
                                        "        return \"text\";\n" +
                                        "    }\n" +
                                        "}");
    PsiElement element =
			TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getAllAccepted());

    assert element != null;
    final String newText = ImplementationViewComponent.getNewText(element);

    assertEquals("    @Override\n" +
                 "    public String toString() {\n" +
                 "        return \"text\";\n" +
                 "    }", newText);
  }

  public void testInnerClasses() {
    myFixture.configureByText("a.java", "abstract class AF<caret>oo{\n" +
                                        "    abstract boolean aaa();\n" +
                                        "    static class AFoo1 extends AFoo {\n" +
                                        "        @Override\n" +
                                        "        boolean aaa() {\n" +
                                        "            return false;\n" +
                                        "        }\n" +
                                        "    }\n" +
                                        "    static class AFoo3 extends AFoo {\n" +
                                        "        @Override\n" +
                                        "        boolean aaa() {\n" +
                                        "            return false;\n" +
                                        "        }\n" +
                                        "    }\n" +
                                        "    static class AFoo2 extends AFoo {\n" +
                                        "        @Override\n" +
                                        "        boolean aaa() {\n" +
                                        "            return false;\n" +
                                        "        }\n" +
                                        "    }\n" +
                                        "    \n" +
                                        "}");
    PsiClass psiClass =
      (PsiClass)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getAllAccepted());

    assert psiClass != null;
    final Collection<PsiClass> classes = ClassInheritorsSearch.search(psiClass).findAll();
    List<PsiClass> all = new ArrayList<PsiClass>();
    all.add(psiClass);
    all.addAll(classes);
    final ImplementationViewComponent component =
      new ImplementationViewComponent(all.toArray(new PsiElement[all.size()]), 0);
    try {
      final String[] visibleFiles = component.getVisibleFiles();
      Assert.assertArrayEquals(Arrays.toString(visibleFiles),
                               new String[]{"a.java (AFoo)", "a.java (AFoo1 in AFoo)", "a.java (AFoo3 in AFoo)", "a.java (AFoo2 in AFoo)"}, visibleFiles);
    }
    finally {
      component.removeNotify();
    }
  }

  public void testMethodsInInnerClasses() {
      myFixture.configureByText("a.java", "abstract class AFoo{\n" +
                                          "    abstract boolean a<caret>aa();\n" +
                                          "    static class AFoo1 extends AFoo {\n" +
                                          "        @Override\n" +
                                          "        boolean aaa() {\n" +
                                          "            return false;\n" +
                                          "        }\n" +
                                          "    }\n" +
                                          "    static class AFoo3 extends AFoo {\n" +
                                          "        @Override\n" +
                                          "        boolean aaa() {\n" +
                                          "            return false;\n" +
                                          "        }\n" +
                                          "    }\n" +
                                          "    static class AFoo2 extends AFoo {\n" +
                                          "        @Override\n" +
                                          "        boolean aaa() {\n" +
                                          "            return false;\n" +
                                          "        }\n" +
                                          "    }\n" +
                                          "    \n" +
                                          "}");
      PsiMethod psiMethod =
        (PsiMethod)TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getAllAccepted());

    assert psiMethod != null;
    final Collection<PsiMethod> methods = OverridingMethodsSearch.search(psiMethod).findAll();
    List<PsiMethod> all = new ArrayList<PsiMethod>();
    all.add(psiMethod);
    all.addAll(methods);

    //make sure they are in predefined order
    Collections.sort(all, new Comparator<PsiMethod>() {
      @Override
      public int compare(PsiMethod o1, PsiMethod o2) {
        return o1.getContainingClass().getQualifiedName()
          .compareTo(o2.getContainingClass().getQualifiedName());
      }
    });
    final ImplementationViewComponent component =
      new ImplementationViewComponent(all.toArray(new PsiElement[all.size()]), 0);
    try {
      final String[] visibleFiles = component.getVisibleFiles();
      Assert.assertArrayEquals(Arrays.toString(visibleFiles),
                               new String[]{"a.java (AFoo)", "a.java (AFoo1 in AFoo)", "a.java (AFoo2 in AFoo)", "a.java (AFoo3 in AFoo)"}, visibleFiles);
    }
    finally {
      component.removeNotify();
    }
  }
}
