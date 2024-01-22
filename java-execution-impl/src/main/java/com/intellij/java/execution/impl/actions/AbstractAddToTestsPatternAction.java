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

import com.intellij.java.execution.impl.JavaTestConfigurationBase;
import com.intellij.java.execution.impl.testframework.AbstractPatternBasedConfigurationProducer;
import consulo.execution.RunManager;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.RunConfiguration;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.LangDataKeys;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import java.util.*;

public abstract class AbstractAddToTestsPatternAction<T extends JavaTestConfigurationBase> extends AnAction {
  @Nonnull
  protected abstract AbstractPatternBasedConfigurationProducer<T> getPatternBasedProducer();

  @Nonnull
  protected abstract ConfigurationType getConfigurationType();

  protected abstract boolean isPatternBasedConfiguration(T configuration);

  protected abstract Set<String> getPatterns(T configuration);

  @RequiredUIAccess
  @Override
  public void actionPerformed(AnActionEvent e) {
    final PsiElement[] psiElements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
    final LinkedHashSet<PsiElement> classes = new LinkedHashSet<>();
    PsiElementProcessor.CollectElements<PsiElement> processor = new PsiElementProcessor.CollectElements<>(classes);
    getPatternBasedProducer().collectTestMembers(psiElements, true, true, processor);

    final Project project = e.getData(CommonDataKeys.PROJECT);
    final List<T> patternConfigurations = collectPatternConfigurations(classes, project);
    if (patternConfigurations.size() == 1) {
      final T configuration = patternConfigurations.get(0);
      for (PsiElement aClass : classes) {
        getPatterns(configuration).add(getPatternBasedProducer().getQName(aClass));
      }
    } else {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<T>("Choose suite to add", patternConfigurations) {
        @Override
        public PopupStep onChosen(T configuration, boolean finalChoice) {
          for (PsiElement aClass : classes) {
            getPatterns(configuration).add(getPatternBasedProducer().getQName(aClass));
          }
          return FINAL_CHOICE;
        }

        @Override
        public Image getIconFor(T configuration) {
          return configuration.getIcon();
        }

        @jakarta.annotation.Nonnull
        @Override
        public String getTextFor(T value) {
          return value.getName();
        }
      }).showInBestPositionFor(e.getDataContext());
    }
  }

  @RequiredUIAccess
  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(false);
    final PsiElement[] psiElements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
    if (psiElements != null) {
      PsiElementProcessor.CollectElementsWithLimit<PsiElement> processor = new PsiElementProcessor.CollectElementsWithLimit<>(2);
      getPatternBasedProducer().collectTestMembers(psiElements, false, false, processor);
      Collection<PsiElement> collection = processor.getCollection();
      if (collection.isEmpty()) {
        return;
      }
      final Project project = e.getData(CommonDataKeys.PROJECT);
      if (project != null) {
        final List<T> foundConfigurations = collectPatternConfigurations(collection, project);
        if (!foundConfigurations.isEmpty()) {
          presentation.setVisible(true);
          if (foundConfigurations.size() == 1) {
            presentation.setText("Add to temp suite: " + foundConfigurations.get(0).getName());
          }
        }
      }
    }
  }

  private List<T> collectPatternConfigurations(Collection<PsiElement> foundClasses, Project project) {
    final List<RunConfiguration> configurations = RunManager.getInstance(project).getConfigurationsList(getConfigurationType());
    final List<T> foundConfigurations = new ArrayList<>();
    for (RunConfiguration configuration : configurations) {
      if (isPatternBasedConfiguration((T) configuration)) {
        if (foundClasses.size() > 1 || foundClasses.size() == 1 && !getPatterns((T) configuration).contains(getPatternBasedProducer().getQName(ContainerUtil.getFirstItem(foundClasses)))) {
          foundConfigurations.add((T) configuration);
        }
      }
    }
    return foundConfigurations;
  }
}
