/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.settings;

import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.IdeaSimpleConfigurable;
import consulo.execution.debug.setting.DebuggerSettingsCategory;
import consulo.execution.debug.setting.XDebuggerSettings;
import consulo.java.language.localize.JavaLanguageLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.jdom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Collections.singletonList;

/**
 * We cannot now transform DebuggerSettings to XDebuggerSettings: getState/loadState is not called for EP,
 * but we cannot use standard implementation to save our state, due to backward compatibility we must use own state spec.
 * <p/>
 * But we must implement createConfigurable as part of XDebuggerSettings otherwise java general settings will be before xdebugger general setting,
 * because JavaDebuggerSettingsPanelProvider has higher priority than XDebuggerSettingsPanelProviderImpl.
 */
@ExtensionImpl
public class JavaDebuggerSettings extends XDebuggerSettings<Element> {
    @Inject
    public JavaDebuggerSettings() {
        super("java");
    }

    @Nonnull
    @Override
    public Collection<? extends Configurable> createConfigurables(@Nonnull DebuggerSettingsCategory category) {
        Supplier<DebuggerSettings> settingsSupplier = DebuggerSettings::getInstance;

        switch (category) {
            case GENERAL:
                return singletonList(new NewDebuggerLaunchingConfigurable(settingsSupplier));
            case DATA_VIEWS:
                return createDataViewsConfigurable();
            case STEPPING:
                return singletonList(IdeaSimpleConfigurable.create("reference.idesettings.debugger.stepping",
                    JavaLanguageLocalize.javaLanguageDisplayName(),
                    DebuggerSteppingConfigurable.class,
                    settingsSupplier));
            case HOTSWAP:
                return singletonList(IdeaSimpleConfigurable.create("reference.idesettings.debugger.hotswap",
                    JavaLanguageLocalize.javaLanguageDisplayName(),
                    JavaHotSwapConfigurableUi.class,
                    settingsSupplier));
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("SpellCheckingInspection")
    @Nonnull
    public static List<Configurable> createDataViewsConfigurable() {
        return Arrays.<Configurable>asList(new DebuggerDataViewsConfigurable(null),
            IdeaSimpleConfigurable.create("reference.idesettings.debugger.typerenderers",
                JavaDebuggerLocalize.userRenderersConfigurableDisplayName(),
                UserRenderersConfigurable.class,
                NodeRendererSettings::getInstance)
        );
    }

    @Override
    public void generalApplied(@Nonnull DebuggerSettingsCategory category) {
        if (category == DebuggerSettingsCategory.DATA_VIEWS) {
            NodeRendererSettings.getInstance().fireRenderersChanged();
        }
    }

    @Nullable
    @Override
    public Element getState() {
        return null;
    }

    @Override
    public void loadState(Element state) {
    }
}
