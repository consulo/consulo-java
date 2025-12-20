package com.intellij.refactoring;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import com.intellij.JavaTestUtil;
import consulo.ide.impl.idea.codeInsight.template.impl.TemplateManagerImpl;
import consulo.language.editor.template.TemplateState;
import com.intellij.java.impl.lang.java.JavaRefactoringSupportProvider;
import consulo.language.editor.WriteCommandAction;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import com.intellij.java.impl.refactoring.rename.JavaNameSuggestionProvider;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import com.intellij.java.impl.refactoring.rename.RenameWrongRefHandler;
import consulo.language.editor.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import consulo.util.collection.ContainerUtil;
import consulo.language.editor.TargetElementUtil;
import consulo.codeInsight.TargetElementUtilEx;
import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public abstract class RenameLocalTest extends LightRefactoringTestCase {
  private static final String BASE_PATH = "/refactoring/renameLocal/";

  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testIDEADEV3320() throws Exception {
    doTest("f");
  }

  public void testIDEADEV13849() throws Exception {
    doTest("aaaaa");
  }

  public void testConflictWithOuterClassField() throws Exception {  // IDEADEV-24564
    doTest("f");
  }

  public void testConflictWithJavadocTag() throws Exception {
    doTest("i");
  }

  public void testRenameLocalIncomplete() throws Exception {
    doTest("_i");
  }

  public void testRenameParamIncomplete() throws Exception {
    doTest("_i");
  }

  public void testClassNameUsedInMethodRefs() throws Exception {
    doTest("Bar1");
  }

  public void testRenameParamUniqueName() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED,TargetElementUtilEx.REFERENCED_ELEMENT_ACCEPTED));
    assertNotNull(element);
    HashSet<String> result = new HashSet<String>();
    new JavaNameSuggestionProvider().getSuggestedNames(element, getFile(), result);
    assertTrue(result.toString(), result.contains("window"));
  }

  private void doTest(String newName) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED , TargetElementUtilEx.REFERENCED_ELEMENT_ACCEPTED));
    assertNotNull(element);
    new RenameProcessor(getProject(), element, newName, true, true).run();
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testRenameInPlaceQualifyFieldReference() throws Exception {
    doTestInplaceRename("myI");
  }
  
  public void testRenameInPlaceQualifyFieldReferenceInChild() throws Exception {
    doTestInplaceRename("myI");
  }
  
  public void testRenameInPlaceThisNeeded() throws Exception {
    doTestInplaceRename("a");
  }

  public void testRenameInPlaceOnRef() throws Exception {
    doTestInplaceRename("a");
  }

  public void testRenameInPlaceParamInOverriderAutomaticRenamer() throws Exception {
    doTestInplaceRename("pp");
  }
  
  public void testRenameFieldWithConstructorParamAutomatic() throws Exception {
    doTest("pp");
  }

  public void testRenameInPlaceParamInOverriderAutomaticRenamerConflict() throws Exception {
    doTestInplaceRename("pp");
  }

  public void testRenameResource() throws Exception {
    doTest("r1");
  }

  public void testRenameResourceInPlace() throws Exception {
    doTestInplaceRename("r1");
  }

  public void testRenameToFieldNameInStaticContext() throws Exception {
    doTestInplaceRename("myFoo");
  }

  public void testRenameInPlaceInStaticContextWithConflictingField() throws Exception {
    doTestInplaceRename("s");
  }

  private void doTestInplaceRename(String newName) throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");

    PsiElement element = TargetElementUtil.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED, TargetElementUtilEx.REFERENCED_ELEMENT_ACCEPTED));
    assertNotNull(element);
    assertTrue("In-place rename not allowed for " + element,
               JavaRefactoringSupportProvider.mayRenameInplace(element, null));

    CodeInsightTestUtil.doInlineRename(new VariableInplaceRenameHandler(), newName, getEditor(), element);

    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testRenameWrongRef() throws Exception {
    doRenameWrongRef("i");
  }

  private void doRenameWrongRef(final String newName) throws Exception {
    String name = getTestName(false);
    configureByFile(BASE_PATH + name + ".java");

    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());

    new RenameWrongRefHandler().invoke(getProject(), getEditor(), getFile(), null);

    TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    assert state != null;
    final TextRange range = state.getCurrentVariableRange();
    assert range != null;

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        getEditor().getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), newName);
      }
    }.execute().throwException();

    state.gotoEnd(false);
    checkResultByFile(BASE_PATH + name + "_after.java");
  }
}
