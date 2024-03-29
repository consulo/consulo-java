package com.intellij.psi.impl.source.tree.java;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.psi.*;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.psi.scope.GlobalSearchScope;

/**
 * @author dsl
 */
public abstract class BindToGenericClassTest extends GenericsTestCase {
  private boolean myOldFQNamesSetting;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setupGenericSampleClasses();
    final CodeStyleSettings currentSettings = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings();

    myOldFQNamesSetting = currentSettings.USE_FQ_CLASS_NAMES;
    currentSettings.USE_FQ_CLASS_NAMES = true;
  }

  @Override
  protected void tearDown() throws Exception {
    final CodeStyleSettings currentSettings = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings();
    currentSettings.USE_FQ_CLASS_NAMES = myOldFQNamesSetting;
    super.tearDown();
  }

  public void testReferenceElement() throws Exception {
    final JavaPsiFacade manager = getJavaFacade();
    final PsiClass classA = manager.getElementFactory().createClassFromText("class A extends List<String>{}", null).getInnerClasses()[0];
    final PsiClass classTestList = manager.findClass("test.List", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(classTestList);
    classA.getExtendsList().getReferenceElements()[0].bindToElement(classTestList);
    assertEquals("class A extends test.List<String>{}", classA.getText());
  }

  public void testReference() throws Exception {
    final JavaPsiFacade manager = getJavaFacade();
    final PsiExpression psiExpression = manager.getElementFactory().createExpressionFromText("List", null);
    final PsiClass classTestList = manager.findClass("test.List", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(classTestList);
    final PsiElement result = ((PsiReferenceExpression) psiExpression).bindToElement(classTestList);
    assertEquals("test.List", result.getText());
  }
}
