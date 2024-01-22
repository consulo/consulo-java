/**
 * @author ven
 */
package com.intellij.refactoring;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import com.intellij.JavaTestUtil;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import com.intellij.java.impl.refactoring.makeStatic.MakeClassStaticProcessor;
import com.intellij.java.impl.refactoring.makeStatic.MakeStaticUtil;
import com.intellij.java.impl.refactoring.makeStatic.Settings;
import com.intellij.java.analysis.impl.refactoring.util.VariableData;
import consulo.util.collection.ContainerUtil;
import consulo.language.editor.TargetElementUtil;
import consulo.codeInsight.TargetElementUtilEx;
import jakarta.annotation.Nonnull;

public abstract class MakeClassStaticTest extends LightRefactoringTestCase {
  private static final String TEST_ROOT = "/refactoring/makeClassStatic/";

  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimple1() throws Exception { perform(); }

  public void testSimpleFields() throws Exception { performWithFields(); }

  public void testFieldInitializerMovedToTheConstructor() throws Exception { performWithFields(); }

  public void testQualifiedThis() throws Exception { perform(); }

  public void testIDEADEV3247() throws Exception { perform(); }

  public void testIDEADEV11595() throws Exception { perform(); }

  public void testIDEADEV12762() throws Exception { perform(); }

  public void testNewExpressionQualifier() throws Exception {perform();}
  public void testThisSuperExpressions() throws Exception {perform();}

  public void testNonDefaultConstructorAnonymousClass() throws Exception {perform();}
  public void testDefaultConstructorAnonymousClass() throws Exception {perform();}
  public void testFieldInitializerSplit() throws Exception {perform();}

  public void testRegReference() throws Exception {
    perform();
  }

  public void testFieldWithMyPrefix() throws Exception {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    String oldPrefix = settings.FIELD_NAME_PREFIX;
    settings.FIELD_NAME_PREFIX = "my";
    try {
      performWithFields();
    }
    finally {
      settings.FIELD_NAME_PREFIX = oldPrefix;
    }
  }

  private void perform() throws Exception {
    configureByFile(TEST_ROOT + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED));
    assertTrue(element instanceof PsiClass);
    PsiClass aClass = (PsiClass)element;

    boolean addClassParameter = MakeStaticUtil.isParameterNeeded(aClass);

    new MakeClassStaticProcessor(
            getProject(),
            aClass,
            new Settings(true, addClassParameter ? "anObject" : null, null)).run();
    checkResultByFile(TEST_ROOT + getTestName(false) + "_after.java");
  }

  private void performWithFields() throws Exception {
    configureByFile(TEST_ROOT + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED));
    assertTrue(element instanceof PsiClass);
    PsiClass aClass = (PsiClass)element;
    final ArrayList<VariableData> parametersForFields = new ArrayList<VariableData>();
    final boolean addClassParameter = MakeStaticUtil.buildVariableData(aClass, parametersForFields);

    new MakeClassStaticProcessor(
            getProject(),
            aClass,
            new Settings(true, addClassParameter ? "anObject" : null,
                         parametersForFields.toArray(
                           new VariableData[parametersForFields.size()]))).run();
    checkResultByFile(TEST_ROOT + getTestName(false) + "_after.java");
  }
}
