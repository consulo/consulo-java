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
package com.intellij.java.impl.lang.properties.psi;

import java.util.List;

import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.language.editor.intention.IntentionAction;
import com.intellij.lang.properties.references.I18nUtil;
import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiFile;

public abstract class ResourceBundleManager {
  private static final ExtensionPointName<ResourceBundleManager> RESOURCE_BUNDLE_MANAGER = ExtensionPointName.create("consulo.java.resourceBundleManager");
  protected final Project myProject;

  protected ResourceBundleManager(final Project project) {
    myProject = project;
  }

  /**
   * By default returns java.util.ResourceBundle class in context JDK
   */
  @Nullable
  public abstract PsiClass getResourceBundle();

  public List<String> suggestPropertiesFiles(){
    return I18nUtil.defaultGetPropertyFiles(myProject);
  }

  @Nullable
  public I18nizedTextGenerator getI18nizedTextGenerator() {
    return null;
  }

  @Nullable
  @NonNls
  public abstract String getTemplateName();

  @Nullable
  @NonNls
  public abstract String getConcatenationTemplateName();

  public abstract boolean isActive(PsiFile context) throws ResourceBundleNotFoundException;

  public abstract boolean canShowJavaCodeInfo();

  @Nullable
  public static ResourceBundleManager getManager(PsiFile context) throws ResourceBundleNotFoundException {
    final Project project = context.getProject();
    final ResourceBundleManager[] managers = project.getExtensions(RESOURCE_BUNDLE_MANAGER);
    for (ResourceBundleManager manager : managers) {
      if (manager.isActive(context)) {
        return manager;
      }
    }
    final DefaultResourceBundleManager manager = new DefaultResourceBundleManager(project);
    return manager.isActive(context) ? manager : null;
  }

  @Nullable
  public PropertyCreationHandler getPropertyCreationHandler() {
    return null;
  }

  @Nullable
  public String suggestPropertyKey(@Nonnull final String value) {
    return null;
  }

  public static class ResourceBundleNotFoundException extends Exception {
    private final IntentionAction myFix;

    public ResourceBundleNotFoundException(final String message, IntentionAction setupResourceBundle) {
      super(message);
      myFix = setupResourceBundle;
    }

    public IntentionAction getFix() {
      return myFix;
    }
  }
}
