/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.testIntegration.intention;

import com.intellij.java.impl.codeInsight.intention.impl.BaseMoveInitializerToMethodAction;
import com.intellij.java.impl.testIntegration.TestIntegrationUtils;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.testIntegration.JavaTestFramework;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author cdr
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MoveInitializerToSetUpMethodAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class MoveInitializerToSetUpMethodAction extends BaseMoveInitializerToMethodAction {
  private static final Logger LOG = Logger.getInstance(MoveInitializerToSetUpMethodAction.class);

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return CodeInsightLocalize.intentionMoveInitializerToSetUp();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    final boolean isAvailable = super.isAvailable(project, editor, element) && TestIntegrationUtils.isTest(element);
    if (isAvailable) {
      final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
      LOG.assertTrue(field != null);
      final PsiClass aClass = field.getContainingClass();
      LOG.assertTrue(aClass != null);
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      for (TestFramework framework : TestFramework.EXTENSION_NAME.getExtensionList()) {
        if (framework instanceof JavaTestFramework testFramework && framework.isTestClass(aClass)) {
          try {
            testFramework.createSetUpPatternMethod(elementFactory);
            return true;
          }
          catch (Exception e) {
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  @Nonnull
  @Override
  protected Collection<String> getUnsuitableModifiers() {
    return Arrays.asList(PsiModifier.STATIC, PsiModifier.FINAL);
  }

  @Nonnull
  @Override
  protected Collection<PsiMethod> getOrCreateMethods(@Nonnull Project project, @Nonnull Editor editor, PsiFile file, @Nonnull PsiClass aClass) {
    final PsiMethod setUpMethod = TestFrameworks.getInstance().findOrCreateSetUpMethod(aClass);
    return setUpMethod == null ? Collections.<PsiMethod>emptyList() : Arrays.asList(setUpMethod);
  }
}
