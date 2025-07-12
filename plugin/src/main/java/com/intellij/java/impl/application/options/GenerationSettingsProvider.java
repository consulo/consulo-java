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
package com.intellij.java.impl.application.options;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.localize.ApplicationLocalize;
import consulo.configurable.Configurable;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.setting.CodeStyleSettingsProvider;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class GenerationSettingsProvider extends CodeStyleSettingsProvider {
    @Override
    @Nonnull
    public Configurable createSettingsPage(final CodeStyleSettings settings, final CodeStyleSettings originalSettings) {
        return new CodeStyleGenerationConfigurable(settings);
    }

    @Nonnull
    @Override
    public LocalizeValue getConfigurableDisplayName() {
        return ApplicationLocalize.titleCodeGeneration();
    }

    @Override
    public boolean hasSettingsPage() {
        return false;
    }

    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }
}
