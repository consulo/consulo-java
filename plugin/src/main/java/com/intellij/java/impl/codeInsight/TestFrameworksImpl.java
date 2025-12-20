/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 28-May-2007
 */
package com.intellij.java.impl.codeInsight;

import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.extension.ExtensionPoint;
import consulo.language.util.IncorrectOperationException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nullable;

@Singleton
@ServiceImpl
public class TestFrameworksImpl extends TestFrameworks {
  private final Application myApplication;

  @Inject
  public TestFrameworksImpl(Application application) {
    myApplication = application;
  }

  @Override
  public boolean isTestClass(PsiClass psiClass) {
    ExtensionPoint<TestFramework> point = myApplication.getExtensionPoint(TestFramework.class);
    return point.findFirstSafe(it -> it.isTestClass(psiClass)) != null;
  }

  @Override
  public boolean isPotentialTestClass(PsiClass psiClass) {
    ExtensionPoint<TestFramework> point = myApplication.getExtensionPoint(TestFramework.class);
    return point.findFirstSafe(it -> it.isPotentialTestClass(psiClass)) != null;
  }

  @Override
  @Nullable
  public PsiMethod findOrCreateSetUpMethod(PsiClass psiClass) {
    ExtensionPoint<TestFramework> point = myApplication.getExtensionPoint(TestFramework.class);

    return point.computeSafeIfAny(framework -> {
      if (framework.isTestClass(psiClass)) {
        try {
          PsiMethod setUpMethod = (PsiMethod)framework.findOrCreateSetUpMethod(psiClass);
          if (setUpMethod != null) {
            return setUpMethod;
          }
        }
        catch (IncorrectOperationException ignored) {
        }
      }

      return null;
    });
  }

  @Override
  @Nullable
  public PsiMethod findSetUpMethod(PsiClass psiClass) {
    ExtensionPoint<TestFramework> point = myApplication.getExtensionPoint(TestFramework.class);

    return point.computeSafeIfAny(framework -> {
      if (framework.isTestClass(psiClass)) {
        PsiMethod setUpMethod = (PsiMethod)framework.findSetUpMethod(psiClass);
        if (setUpMethod != null) {
          return setUpMethod;
        }
      }

      return null;
    });
  }

  @Override
  @Nullable
  public PsiMethod findTearDownMethod(PsiClass psiClass) {
    ExtensionPoint<TestFramework> point = myApplication.getExtensionPoint(TestFramework.class);

    return point.computeSafeIfAny(framework -> {
      if (framework.isTestClass(psiClass)) {
        PsiMethod setUpMethod = (PsiMethod)framework.findTearDownMethod(psiClass);
        if (setUpMethod != null) {
          return setUpMethod;
        }
      }

      return null;
    });
  }

  @Override
  protected boolean hasConfigMethods(PsiClass psiClass) {
    ExtensionPoint<TestFramework> point = myApplication.getExtensionPoint(TestFramework.class);

    return point.findFirstSafe(framework -> {
      if (framework.findSetUpMethod(psiClass) != null || framework.findTearDownMethod(psiClass) != null) {
        return true;
      }
      return false;
    }) != null;
  }

  @Override
  public boolean isTestMethod(PsiMethod method) {
    ExtensionPoint<TestFramework> point = myApplication.getExtensionPoint(TestFramework.class);

    return point.computeSafeIfAny(testFramework -> testFramework.isTestMethod(method)) != null;
  }
}