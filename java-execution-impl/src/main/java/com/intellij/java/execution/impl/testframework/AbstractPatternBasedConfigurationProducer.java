/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import consulo.annotation.access.RequiredReadAction;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.configuration.ConfigurationType;
import com.intellij.java.execution.impl.JavaTestConfigurationBase;
import consulo.execution.test.TestsUIUtil;
import consulo.dataContext.DataContext;
import consulo.util.lang.Comparing;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.util.PsiTreeUtil;

import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractPatternBasedConfigurationProducer<T extends JavaTestConfigurationBase> extends AbstractJavaTestConfigurationProducer<T> implements Cloneable {
  public AbstractPatternBasedConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @RequiredReadAction
  public boolean isConfiguredFromContext(ConfigurationContext context, Set<String> patterns) {
    final LinkedHashSet<String> classes = new LinkedHashSet<>();
    final DataContext dataContext = context.getDataContext();
    if (TestsUIUtil.isMultipleSelectionImpossible(dataContext)) {
      return false;
    }
    final PsiElement[] locationElements = collectLocationElements(classes, dataContext);
    if (locationElements == null) {
      collectContextElements(dataContext, true, false, classes, new PsiElementProcessor.CollectElements<>());
    }
    if (Comparing.equal(classes, patterns)) {
      if (patterns.size() == 1) {
        final String pattern = patterns.iterator().next();
        if (!pattern.contains(",")) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(dataContext.getData(PsiElement.KEY), PsiMethod.class);
          return method != null && isTestMethod(false, method);
        }
      }
      return true;
    }
    return false;
  }

  @RequiredReadAction
  public PsiElement checkPatterns(ConfigurationContext context, LinkedHashSet<String> classes) {
    PsiElement[] result;
    final DataContext dataContext = context.getDataContext();
    if (TestsUIUtil.isMultipleSelectionImpossible(dataContext)) {
      return null;
    }
    final PsiElement[] locationElements = collectLocationElements(classes, dataContext);
    PsiElementProcessor.CollectElements<PsiElement> processor = new PsiElementProcessor.CollectElements<>();
    if (locationElements != null) {
      collectTestMembers(locationElements, false, true, processor);
      result = processor.toArray();
    } else if (collectContextElements(dataContext, true, true, classes, processor)) {
      result = processor.toArray();
    } else {
      return null;
    }
    if (result.length <= 1) {
      return null;
    }
    return result[0];
  }
}
