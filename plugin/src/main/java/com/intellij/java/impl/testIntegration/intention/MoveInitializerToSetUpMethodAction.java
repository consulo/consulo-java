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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.impl.codeInsight.intention.impl.BaseMoveInitializerToMethodAction;
import com.intellij.java.language.psi.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.java.language.testIntegration.JavaTestFramework;
import com.intellij.java.language.testIntegration.TestFramework;
import com.intellij.java.impl.testIntegration.TestIntegrationUtils;
import consulo.logging.Logger;

import javax.annotation.Nonnull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author cdr
 */
public class MoveInitializerToSetUpMethodAction extends BaseMoveInitializerToMethodAction {
  private static final Logger LOG = Logger.getInstance(MoveInitializerToSetUpMethodAction.class);

  @Override
  @Nonnull
  public String getFamilyName() {
    return getText();
  }

  @Override
  @Nonnull
  public String getText() {
    return CodeInsightBundle.message("intention.move.initializer.to.set.up");
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
      for (TestFramework framework : Extensions.getExtensions(TestFramework.EXTENSION_NAME)) {
        if (framework instanceof JavaTestFramework && framework.isTestClass(aClass)) {
          try {
            ((JavaTestFramework)framework).createSetUpPatternMethod(elementFactory);
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
