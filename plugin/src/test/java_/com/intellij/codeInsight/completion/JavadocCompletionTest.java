package com.intellij.codeInsight.completion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import jakarta.annotation.Nonnull;
import com.intellij.JavaTestUtil;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.completion.lookup.LookupElement;
import com.intellij.java.impl.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.java.language.JavaLanguage;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.ide.impl.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import consulo.language.psi.ReferenceProvidersRegistry;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.util.lang.ObjectUtil;
import consulo.language.util.ProcessingContext;


/**
 * @author mike
 */
public abstract class JavadocCompletionTest extends LightFixtureCompletionTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/javadoc/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JavaDocLocalInspection());
  }

  public void testNamesInClass() throws Exception {
    configureByFile("ClassTagName.java");
    assertStringItems("author", "deprecated", "param", "see", "serial", "since", "version");
  }

  public void testNamesInField() throws Exception {
    configureByFile("FieldTagName.java");
    assertStringItems("deprecated", "see", "serial", "serialField", "since");
  }

  public void testNamesInMethod0() throws Exception {
    configureByFile("MethodTagName0.java");
    assertStringItems("deprecated", "exception", "param", "return", "see", "serialData", "since", "throws");
  }

  public void testNamesInMethod1() throws Exception {
    configureByFile("MethodTagName1.java");
    assertStringItems("see", "serialData", "since", "class", "throws");
  }

  public void testParamValueCompletion() throws Exception {
    configureByFile("ParamValue0.java");
    assertStringItems("a", "b", "c");
  }

  public void testParamValueWithPrefixCompletion() throws Exception {
    configureByFile("ParamValue1.java");
    assertStringItems("a1", "a2", "a3");
  }

  public void testDescribedParameters() throws Exception {
    configureByFile("ParamValue2.java");
    assertStringItems("a2", "a3");
  }

  public void testSee0() throws Exception {
    configureByFile("See0.java");
    myFixture.assertPreferredCompletionItems(0, "foo", "clone", "equals", "hashCode");
  }

  public void testSee1() throws Exception {
    configureByFile("See1.java");
    assertStringItems("notify", "notifyAll");
  }

  public void testSee2() throws Exception {
    configureByFile("See2.java");
    assertStringItems("notify", "notifyAll");
  }

  public void testSee3() throws Exception {
    configureByFile("See3.java");

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myField")));
  }

  @Nonnull
  private List<String> getLookupElementStrings() {
    return ObjectUtil.assertNotNull(myFixture.getLookupElementStrings());
  }

  public void testSee4() throws Exception {
    configureByFile("See4.java");

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("A", "B", "C")));
  }

  public void testSee5() throws Exception {
    configureByFile("See5.java");

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myName")));
  }

  public void testIDEADEV10620() throws Exception {
    configureByFile("IDEADEV10620.java");

    checkResultByFile("IDEADEV10620-after.java");
  }

  public void testException0() throws Exception {
    configureByFile("Exception0.java");
    assertStringItems("deprecated", "exception", "param", "see", "serialData", "since", "throws");
  }

  public void testException1() throws Exception {
    configureByFile("Exception1.java");
    assertTrue(myItems.length > 18);
  }

  public void testException2() throws Exception {
    myFixture.configureByFile("Exception2.java");
    myFixture.complete(CompletionType.SMART);
    assertStringItems("IllegalStateException", "IOException");
  }

  public void testInlineLookup() throws Exception {
    configureByFile("InlineTagName.java");
    assertStringItems("code", "docRoot", "inheritDoc", "link", "linkplain", "literal", "value");
  }

  public void testFinishWithSharp() throws Throwable {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    try {
      checkFinishWithSharp();
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old;
    }
  }

  private void checkFinishWithSharp() throws Exception {
    myFixture.configureByFile("FinishWithSharp.java");
    myFixture.completeBasic();
    type('#');
    checkResultByFile("FinishWithSharp_after.java");
    final List<LookupElement> items = getLookup().getItems();
    assertEquals("bar", items.get(0).getLookupString());
    assertEquals("foo", items.get(1).getLookupString());
  }

  public void testShortenClassName() throws Throwable {
    CodeStyleSettingsManager.getSettings(getProject()).USE_FQ_CLASS_NAMES_IN_JAVADOC = false;
    try {
      doTest();
    }
    finally {
      CodeStyleSettingsManager.getSettings(getProject()).USE_FQ_CLASS_NAMES_IN_JAVADOC = true;
    }
  }

  public void testMethodBeforeSharp() throws Throwable {
    doTest();
  }

  public void testFieldReferenceInInnerClassJavadoc() throws Throwable {
    doTest();
  }

  public void testShortenClassReference() throws Throwable { doTest(); }
  public void testQualifiedClassReference() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    checkResultByFile(getTestName(false) + "_after.java");
  }
  public void testThrowsNonImported() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doTest() throws Exception {
    configureByFile(getTestName(false) + ".java");
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testInlinePackageReferenceCompletion() throws Exception {
    configureByFile("InlineReference.java");
    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("io", "lang", "util")));
  }

  public void testQualifyClassReferenceInPackageStatement() throws Exception {
    configureByFile(getTestName(false) + ".java");
    myFixture.type('\n');
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testCustomReferenceProvider() throws Exception {
    PsiReferenceRegistrarImpl registrar =
      (PsiReferenceRegistrarImpl) ReferenceProvidersRegistry.getInstance().getRegistrar(JavaLanguage.INSTANCE);
    PsiReferenceProvider provider = new PsiReferenceProvider() {
      @Override
      @Nonnull
      public PsiReference[] getReferencesByElement(@Nonnull final PsiElement element, @Nonnull final ProcessingContext context) {
        return new PsiReference[]{new PsiReferenceBase<PsiElement>(element) {

          @Override
          public PsiElement resolve() {
            return element;
          }

          @Override
          @Nonnull
          public Object[] getVariants() {
            return new Object[]{"1", "2", "3"};
          }
        }};
      }
    };
    try {
      registrar.registerReferenceProvider(PsiDocTag.class, provider);
      configureByFile("ReferenceProvider.java");
      assertStringItems("1", "2", "3");
    }
    finally {
      registrar.unregisterReferenceProvider(PsiDocTag.class, provider);
    }
  }
}
