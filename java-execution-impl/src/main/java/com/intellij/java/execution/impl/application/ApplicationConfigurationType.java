/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.application;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.configuration.RunConfiguration;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.image.Image;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@ExtensionImpl
public class ApplicationConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;

  public ApplicationConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      @Override
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new ApplicationConfiguration("", project, ApplicationConfigurationType.this);
      }

      @Override
      public boolean isApplicable(@Nonnull Project project) {
        return ModuleExtensionHelper.getInstance(project).hasModuleExtension(JavaModuleExtension.class);
      }

      @Override
      public void onNewConfigurationCreated(@Nonnull RunConfiguration configuration) {
        ((ModuleBasedConfiguration) configuration).onNewConfigurationCreated();
      }
    };
  }

  @Override
  public LocalizeValue getDisplayName() {
    return JavaExecutionLocalize.applicationConfigurationName();
  }

  @Override
  public LocalizeValue getConfigurationTypeDescription() {
    return JavaExecutionLocalize.applicationConfigurationDescription();
  }

  @Override
  public Image getIcon() {
    return PlatformIconGroup.runconfigurationsApplication();
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @Nullable
  public static PsiClass getMainClass(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiClass) {
        final PsiClass aClass = (PsiClass) element;
        if (PsiMethodUtil.findMainInClass(aClass) != null) {
          return aClass;
        }
      } else if (element instanceof PsiJavaFile) {
        final PsiJavaFile javaFile = (PsiJavaFile) element;
        final PsiClass[] classes = javaFile.getClasses();
        for (PsiClass aClass : classes) {
          if (PsiMethodUtil.findMainInClass(aClass) != null) {
            return aClass;
          }
        }
      }
      element = element.getParent();
    }
    return null;
  }


  @Override
  @Nonnull
  @NonNls
  public String getId() {
    return "JavaApplication";
  }

  @Nullable
  public static ApplicationConfigurationType getInstance() {
    return EP_NAME.findExtensionOrFail(ApplicationConfigurationType.class);
  }
}
