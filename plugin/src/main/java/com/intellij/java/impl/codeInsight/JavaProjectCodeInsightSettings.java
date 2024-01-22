/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.ServiceManager;
import consulo.language.editor.CodeInsightSettings;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.PatternUtil;
import consulo.util.xml.serializer.XmlSerializerUtil;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Tag;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.TestOnly;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@State(name = "JavaProjectCodeInsightSettings", storages = @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/codeInsightSettings.xml"))
public class JavaProjectCodeInsightSettings implements PersistentStateComponent<JavaProjectCodeInsightSettings> {
  private static final ConcurrentMap<String, Pattern> ourPatterns = ConcurrentFactoryMap.createWeakMap(PatternUtil::fromMask);

  @Tag("excluded-names")
  @AbstractCollection(surroundWithTag = false, elementTag = "name", elementValueAttribute = "")
  public List<String> excludedNames = new ArrayList<>();

  public static JavaProjectCodeInsightSettings getSettings(@jakarta.annotation.Nonnull Project project) {
    return ServiceManager.getService(project, JavaProjectCodeInsightSettings.class);
  }

  public boolean isExcluded(@jakarta.annotation.Nonnull String name) {
    for (String excluded : excludedNames) {
      if (nameMatches(name, excluded)) {
        return true;
      }
    }
    for (String excluded : CodeInsightSettings.getInstance().EXCLUDED_PACKAGES) {
      if (nameMatches(name, excluded)) {
        return true;
      }
    }

    return false;
  }

  private static boolean nameMatches(@jakarta.annotation.Nonnull String name, String excluded) {
    int length = getMatchingLength(name, excluded);
    return length > 0 && (name.length() == length || name.charAt(length) == '.');
  }

  private static int getMatchingLength(@Nonnull String name, String excluded) {
    if (name.startsWith(excluded)) {
      return excluded.length();
    }

    if (excluded.indexOf('*') > 0) {
      Matcher matcher = ourPatterns.get(excluded).matcher(name);
      if (matcher.lookingAt()) {
        return matcher.end();
      }
    }

    return -1;
  }

  @Nullable
  @Override
  public JavaProjectCodeInsightSettings getState() {
    return this;
  }

  @Override
  public void loadState(JavaProjectCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @TestOnly
  public static void setExcludedNames(Project project, Disposable parentDisposable, String... excludes) {
    final JavaProjectCodeInsightSettings instance = getSettings(project);
    assert instance.excludedNames.isEmpty();
    instance.excludedNames = Arrays.asList(excludes);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        instance.excludedNames = ContainerUtil.newArrayList();
      }
    });
  }
}
