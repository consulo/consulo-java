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
package com.intellij.java.impl.generate.template.toString;

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
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
@State(
  name = "ToStringTemplates",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/toStringTemplates.xml")
  })
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class ToStringTemplatesManager extends TemplatesManager {
  private static final String DEFAULT_CONCAT = "DefaultConcatMember.vm";
  private static final String DEFAULT_CONCAT_GROOVY = "DefaultConcatMemberGroovy.vm";
  private static final String DEFAULT_CONCAT_SUPER = "DefaultConcatMemberSuper.vm";
  private static final String DEFAULT_BUFFER = "DefaultBuffer.vm";
  private static final String DEFAULT_BUILDER = "DefaultBuilder.vm";
  private static final String DEFAULT_TOSTRINGBUILDER = "DefaultToStringBuilder.vm";
  private static final String DEFAULT_TOSTRINGBUILDER3 = "DefaultToStringBuilder3.vm";
  private static final String DEFAULT_GUAVA = "DefaultGuava.vm";
  private static final String DEFAULT_GUAVA_18 = "DefaultGuava18.vm";

  public static TemplatesManager getInstance() {
    return ServiceManager.getService(ToStringTemplatesManager.class);
  }

  @Override
  public TemplateResource[] getDefaultTemplates() {
    try {
      return new TemplateResource[]{
        new TemplateResource("String concat (+)", readFile(DEFAULT_CONCAT), true),
        new TemplateResource("String concat (+) and super.toString()", readFile(DEFAULT_CONCAT_SUPER), true),
        new TemplateResource("StringBuffer", readFile(DEFAULT_BUFFER), true),
        new TemplateResource("StringBuilder (JDK 1.5)", readFile(DEFAULT_BUILDER), true),
        new TemplateResource("ToStringBuilder (Apache commons-lang)", readFile(DEFAULT_TOSTRINGBUILDER), true),
        new TemplateResource("ToStringBuilder (Apache commons-lang 3)", readFile(DEFAULT_TOSTRINGBUILDER3), true),
        new TemplateResource("Objects.toStringHelper (Guava)", readFile(DEFAULT_GUAVA), true),
        new TemplateResource("MoreObjects.toStringHelper (Guava 18+)", readFile(DEFAULT_GUAVA_18), true),
        new TemplateResource("Groovy: String concat (+)", readFile(DEFAULT_CONCAT_GROOVY), true),
      };
    }
    catch (IOException e) {
      throw new TemplateResourceException("Error loading default templates", e);
    }
  }

  protected static String readFile(String resource) throws IOException {
    return readFile(resource, ToStringTemplatesManager.class);
  }
}
