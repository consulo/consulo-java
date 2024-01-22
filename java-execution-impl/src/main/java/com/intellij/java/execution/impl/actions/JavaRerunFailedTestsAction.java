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
package com.intellij.java.execution.impl.actions;

import com.intellij.java.execution.impl.testframework.JavaAwareFilter;
import consulo.execution.test.Filter;
import consulo.execution.test.TestConsoleProperties;
import consulo.execution.test.action.AbstractRerunFailedTestsAction;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.ui.ex.ComponentContainer;
import jakarta.annotation.Nonnull;

/**
 * @author anna
 * @since 24-Dec-2008
 */
public class JavaRerunFailedTestsAction extends AbstractRerunFailedTestsAction {
  public JavaRerunFailedTestsAction(@jakarta.annotation.Nonnull ComponentContainer componentContainer, @jakarta.annotation.Nonnull TestConsoleProperties consoleProperties) {
    super(componentContainer);
    init(consoleProperties);
  }

  @jakarta.annotation.Nonnull
  @Override
  protected Filter getFilter(@jakarta.annotation.Nonnull Project project, @Nonnull GlobalSearchScope searchScope) {
    return super.getFilter(project, searchScope).and(JavaAwareFilter.METHOD(project, searchScope));
  }
}
