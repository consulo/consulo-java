/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import com.intellij.JavaTestUtil;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.impl.refactoring.makeStatic.MakeMethodStaticProcessor;
import com.intellij.java.impl.refactoring.makeStatic.MakeStaticUtil;
import com.intellij.java.impl.refactoring.makeStatic.Settings;
import com.intellij.java.analysis.impl.refactoring.util.VariableData;
import consulo.util.collection.ContainerUtil;
import consulo.language.editor.TargetElementUtil;
import consulo.codeInsight.TargetElementUtilEx;
import jakarta.annotation.Nonnull;

public abstract class MakeMethodStaticTest extends LightRefactoringTestCase {
  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testEmptyMethod() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before1.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after1.java");
  }

  public void testUseStatic() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before2.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after2.java");
  }

  public void testUseField() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before3.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after3.java");
  }

  public void testIDEADEV2556() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before21.java");
    perform(false);
    checkResultByFile("/refactoring/makeMethodStatic/after21.java");
  }

  public void testUseFieldWithThis() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before4.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after4.java");
  }

  public void testUseFieldWithSuperEmptyExtends() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before5.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after5.java");
  }

  public void testUseFieldWithSuper() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before6.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after6.java");
  }

  public void testUseMethod() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before7.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after7.java");
  }

  public void testThisInsideAnonymous() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before8.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after8.java");
  }

  public void testUsageInSubclass() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before9.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after9.java");
  }

  public void testGeneralUsageNoParam() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before10.java");
    perform(false);
    checkResultByFile("/refactoring/makeMethodStatic/after10-np.java");
  }

  public void testGeneralUsage() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before10.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after10.java");
  }

  public void testUsageInSubclassWithSuper() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before11.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after11.java");
  }

  public void testSuperUsageWithComplexSuperClass() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before12.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after12.java");
  }

  public void testExplicitThis() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before13.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after13.java");
  }

  public void testQualifiedThis() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before14.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after14.java");
  }

  public void testSCR8043() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before15.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after15.java");
  }

  public void testJavadoc1() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before16.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after16.java");
  }

  public void testJavadoc2() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before17.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after17.java");
  }

  public void testGenericClass() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before18.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after18.java");
  }

  public void testFieldWriting() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before19.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after19.java");
  }

  public void testQualifiedInnerClassCreation() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before20.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after20.java");
  }

  public void testQualifiedThisAdded() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before22.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after22.java");
  }

  public void testPreserveTypeParams() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/beforePreserveTypeParams.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/afterPreserveTypeParams.java");
  } 

  public void testInnerStaticClassUsed() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/beforeInnerStaticClassUsed.java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED));
    assertTrue(element instanceof PsiMethod);
    assertFalse(MakeStaticUtil.isParameterNeeded((PsiMethod)element));
  }

  public void testMethodReference() throws Exception {
    doTest(true);
  }

  public void testPreserveParametersAlignment() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean addClassParameter) throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before" + getTestName(false) + ".java");
    perform(addClassParameter);
    checkResultByFile("/refactoring/makeMethodStatic/after" + getTestName(false) + ".java");
  }

  private static void perform(boolean addClassParameter) {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED));
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod) element;

    new MakeMethodStaticProcessor(
            getProject(),
            method,
            new Settings(true, addClassParameter ? "anObject" : null, null)).run();
  }

  private static void performWithFields() {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED));
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod) element;
    final ArrayList<VariableData> parametersForFields = new ArrayList<VariableData>();
    final boolean addClassParameter = MakeStaticUtil.buildVariableData(method, parametersForFields);

    new MakeMethodStaticProcessor(
            getProject(),
            method,
            new Settings(true, addClassParameter ? "anObject" : null,
                         parametersForFields.toArray(
                           new VariableData[parametersForFields.size()]))).run();
  }
}
