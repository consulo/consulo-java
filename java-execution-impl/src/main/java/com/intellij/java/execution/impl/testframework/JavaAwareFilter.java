/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.testframework;

import consulo.execution.action.Location;
import consulo.execution.action.PsiLocation;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import consulo.execution.test.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.scope.GlobalSearchScope;

import javax.annotation.Nonnull;

/**
 * @author anna
 * @since 20-Feb-2008
 */
public class JavaAwareFilter {
  private JavaAwareFilter() {
  }

  public static Filter METHOD(@Nonnull final Project project, @Nonnull final GlobalSearchScope searchScope) {
    return new Filter() {
      @Override
      public boolean shouldAccept(final AbstractTestProxy test) {
        Location location = test.getLocation(project, searchScope);
        return location instanceof MethodLocation || location instanceof PsiLocation && location.getPsiElement() instanceof PsiMethod;
      }
    };
  }
}