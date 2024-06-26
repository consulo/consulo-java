/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.folding.impl;

import com.intellij.java.language.impl.codeInsight.folding.impl.JavaCodeFoldingSettingsBase;
import consulo.annotation.component.ServiceImpl;
import jakarta.inject.Singleton;

import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.util.xml.serializer.XmlSerializerUtil;

@Singleton
@State(name = "JavaCodeFoldingSettings", storages = @Storage("editor.codeinsight.xml"))
@ServiceImpl
public class JavaCodeFoldingSettingsImpl extends JavaCodeFoldingSettingsBase implements PersistentStateComponent<JavaCodeFoldingSettingsImpl> {
  @Override
  public JavaCodeFoldingSettingsImpl getState() {
    return this;
  }

  @Override
  public void loadState(final JavaCodeFoldingSettingsImpl state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
