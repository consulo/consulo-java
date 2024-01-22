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
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.java.impl.profile.codeInspection.ui;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationBundle;
import consulo.application.ui.setting.AdditionalEditorGeneralSettingProvider;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.language.editor.DaemonCodeAnalyzerSettings;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;

import jakarta.annotation.Nonnull;
import java.util.function.Consumer;

@ExtensionImpl
public class JavaErrorOptionsProvider implements AdditionalEditorGeneralSettingProvider {
  @Override
  @RequiredUIAccess
  public void fillProperties(@Nonnull SimpleConfigurableByProperties.PropertyBuilder propertyBuilder, @Nonnull Consumer<Component> consumer) {
    CheckBox suppressWay = CheckBox.create(ApplicationBundle.message("checkbox.suppress.with.suppresswarnings"));
    consumer.accept(suppressWay);

    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();

    propertyBuilder.add(suppressWay, settings::isSuppressWarnings, settings::setSuppressWarnings);
  }
}