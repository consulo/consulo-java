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
package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.impl.generate.exception.TemplateResourceException;
import com.intellij.java.impl.generate.template.TemplateResource;
import com.intellij.java.impl.generate.template.TemplatesManager;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import jakarta.annotation.Nonnull;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
@State(
  name = "GetterTemplates",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/getterTemplates.xml")
  })
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GetterTemplatesManager extends TemplatesManager {
  @Nonnull
  public static GetterTemplatesManager getInstance() {
    return ServiceManager.getService(GetterTemplatesManager.class);
  }

  private static final String DEFAULT = "defaultGetter.vm";

  @Override
  public TemplateResource[] getDefaultTemplates() {
    try {
      return new TemplateResource[]{
        new TemplateResource("Default", readFile(DEFAULT), true),
      };
    }
    catch (IOException e) {
      throw new TemplateResourceException("Error loading default templates", e);
    }
  }

  protected static String readFile(String resource) throws IOException {
    return readFile(resource, GetterTemplatesManager.class);
  }
}
