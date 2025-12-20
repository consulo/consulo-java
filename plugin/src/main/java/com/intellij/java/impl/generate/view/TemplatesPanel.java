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

/*
 * @author max
 */
package com.intellij.java.impl.generate.view;

import com.intellij.java.impl.generate.template.TemplateResource;
import com.intellij.java.impl.generate.template.TemplatesManager;
import com.intellij.java.impl.generate.template.toString.ToStringTemplatesManager;
import com.intellij.java.language.psi.PsiType;
import consulo.configurable.ConfigurationException;
import consulo.configurable.UnnamedConfigurable;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.NamedItemsListEditor;
import consulo.util.collection.HashingStrategy;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Supplier;

public class TemplatesPanel extends NamedItemsListEditor<TemplateResource> {
  private static final Namer<TemplateResource> NAMER = new Namer<TemplateResource>() {
    @Override
    public String getName(TemplateResource templateResource) {
      return templateResource.getFileName();
    }

    @Override
    public boolean canRename(TemplateResource item) {
      return !item.isDefault();
    }

    @Override
    public void setName(TemplateResource templateResource, String name) {
      templateResource.setFileName(name);
    }
  };

  private static final Supplier<TemplateResource> FACTORY = new Supplier<TemplateResource>() {
    @Override
    public TemplateResource get() {
      return new TemplateResource();
    }
  };

  private static final Cloner<TemplateResource> CLONER = new Cloner<TemplateResource>() {
    @Override
    public TemplateResource cloneOf(TemplateResource templateResource) {
      if (templateResource.isDefault()) {
        return templateResource;
      }
      return copyOf(templateResource);
    }

    @Override
    public TemplateResource copyOf(TemplateResource templateResource) {
      TemplateResource result = new TemplateResource();
      result.setFileName(templateResource.getFileName());
      result.setTemplate(templateResource.getTemplate());
      return result;
    }
  };

  private static final HashingStrategy<TemplateResource> COMPARER = new HashingStrategy<TemplateResource>() {
    @Override
    public boolean equals(TemplateResource o1, TemplateResource o2) {
      return Comparing.equal(o1.getTemplate(), o2.getTemplate()) && Comparing.equal(o1.getFileName(), o2.getFileName());
    }
  };
  private final Project myProject;
  private final TemplatesManager myTemplatesManager;

  public TemplatesPanel(Project project) {
    this(project, ToStringTemplatesManager.getInstance());
  }

  public TemplatesPanel(Project project, TemplatesManager templatesManager) {
    super(NAMER, FACTORY, CLONER, COMPARER, new ArrayList<TemplateResource>(templatesManager.getAllTemplates()), () -> null);

    //ServiceManager.getService(project, MasterDetailsStateService.class).register("ToStringTemplates.UI", this);
    myProject = project;
    myTemplatesManager = templatesManager;
  }

  @Override
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Templates");
  }

  @Override
  protected String subjDisplayName() {
    return "template";
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "Templates Dialog";
  }

  @Override
  public boolean isModified() {
    return super.isModified() || !Comparing.equal(myTemplatesManager.getDefaultTemplate(), getSelectedItem());
  }

  @Override
  protected boolean canDelete(TemplateResource item) {
    return !item.isDefault();
  }

  @Override
  protected UnnamedConfigurable createConfigurable(TemplateResource item) {
    return new GenerateTemplateConfigurable(item, Collections.<String, PsiType>emptyMap(), myProject, onMultipleFields());
  }

  protected boolean onMultipleFields() {
    return true;
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    myTemplatesManager.setTemplates(getItems());
    TemplateResource selection = getSelectedItem();
    if (selection != null) {
      myTemplatesManager.setDefaultTemplate(selection);
    }
  }
}
