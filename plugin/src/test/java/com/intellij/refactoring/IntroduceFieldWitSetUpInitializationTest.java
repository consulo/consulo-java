/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 17-Oct-2007
 */
package com.intellij.refactoring;

import java.io.File;

import org.junit.Before;
import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import consulo.module.Module;
import consulo.ide.impl.idea.openapi.roots.ModuleRootModificationUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.java.impl.refactoring.introduceField.LocalToFieldHandler;
import com.intellij.util.PathUtil;

public abstract class IntroduceFieldWitSetUpInitializationTest extends CodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected Module createModule(final String name) {
    final Module module = super.createModule(name);
    final String url = VfsUtil.getUrlForLibraryRoot(new File(PathUtil.getJarPathForClass(Before.class)));
    ModuleRootModificationUtil.addModuleLibrary(module, url);
    return module;
  }

  public void testInSetUp() throws Exception {
    doTest();
  }

  public void testInitiallyInSetUp() throws Exception {
    doTest();
  }

  public void testPublicBaseClassSetUp() throws Exception {
    doTest();
  }

  public void testBeforeExist() throws Exception {
    doTest();
  }

  public void testBeforeNotExist() throws Exception {
    doTest();
  }

  public void testBeforeNotExist1() throws Exception {
    doTest();
  }

  public void testOrderInSetup() throws Exception {
    doTest();
  }

  public void testBeforeExistNonAnnotated() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    configureByFile("/refactoring/introduceField/before" + getTestName(false) + ".java");
    final PsiLocalVariable local =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
    new LocalToFieldHandler(getProject(), false) {
      @Override
      protected BaseExpressionToFieldHandler.Settings showRefactoringDialog(final PsiClass aClass,
                                                                            final PsiLocalVariable local,
                                                                            final PsiExpression[] occurences,
                                                                            final boolean isStatic) {
        return new BaseExpressionToFieldHandler.Settings("i", null, occurences, true, false, false,
                                                         BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD,
                                                         PsiModifier.PRIVATE, local, local.getType(), true, (BaseExpressionToFieldHandler.TargetDestination)null, false,
                                                         false);
      }
    }.convertLocalToField(local, myEditor);
    checkResultByFile("/refactoring/introduceField/after" + getTestName(false)+ ".java");
  }
}
