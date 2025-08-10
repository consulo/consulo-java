/*
 * Copyright 2013-2017 consulo.io
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

package consulo.java.debugger.impl.settings;

import com.intellij.java.debugger.impl.settings.ThreadsViewSettings;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.configurable.Configurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

/**
 * @author VISTALL
 * @since 19-Nov-17
 */
public class ThreadsViewConfigurable extends SimpleConfigurableByProperties implements Configurable {
    private final Provider<ThreadsViewSettings> myThreadsViewSettingsProvider;

    @Inject
    public ThreadsViewConfigurable(Provider<ThreadsViewSettings> threadsViewSettingsProvider) {
        myThreadsViewSettingsProvider = threadsViewSettingsProvider;
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, @Nonnull Disposable uiDisposable) {
        ThreadsViewSettings settings = myThreadsViewSettingsProvider.get();

        VerticalLayout rootLayout = VerticalLayout.create();

        VerticalLayout viewLayout = VerticalLayout.create();
        rootLayout.add(LabeledLayout.create(LocalizeValue.localizeTODO("View"), viewLayout));

        CheckBox showThreadGroups = CheckBox.create(JavaDebuggerLocalize.labelThreadsViewConfigurableShowThreadGroups());
        propertyBuilder.add(showThreadGroups, () -> settings.SHOW_THREAD_GROUPS, value -> settings.SHOW_THREAD_GROUPS = value);
        viewLayout.add(showThreadGroups);

        CheckBox showSynthetic = CheckBox.create(JavaDebuggerLocalize.labelThreadsViewConfigurableShowStackFramesForSyntheticMethods());
        propertyBuilder.add(showSynthetic, () -> settings.SHOW_SYNTHETIC_FRAMES, value -> settings.SHOW_SYNTHETIC_FRAMES = value);
        viewLayout.add(showSynthetic);

        CheckBox moveCurrentThreadToTop = CheckBox.create(JavaDebuggerLocalize.labelThreadsViewConfigurableCurrentThreadOnTop());
        propertyBuilder.add(moveCurrentThreadToTop, () -> settings.SHOW_CURRENT_THREAD, value -> settings.SHOW_CURRENT_THREAD = value);
        viewLayout.add(moveCurrentThreadToTop);

        VerticalLayout presentationView = VerticalLayout.create();
        rootLayout.add(LabeledLayout.create(LocalizeValue.localizeTODO("Presentation"), presentationView));

        CheckBox showLineNumbers = CheckBox.create(JavaDebuggerLocalize.labelThreadsViewConfigurableShowLineNumber());
        propertyBuilder.add(showLineNumbers, () -> settings.SHOW_LINE_NUMBER, value -> settings.SHOW_LINE_NUMBER = value);
        presentationView.add(showLineNumbers);

        CheckBox showClassName = CheckBox.create(JavaDebuggerLocalize.labelThreadsViewConfigurableShowClassName());
        propertyBuilder.add(showClassName, () -> settings.SHOW_CLASS_NAME, value -> settings.SHOW_CLASS_NAME = value);
        presentationView.add(showClassName);

        CheckBox showPackageName = CheckBox.create(JavaDebuggerLocalize.labelThreadsViewConfigurableShowPackage());
        propertyBuilder.add(showPackageName, () -> settings.SHOW_PACKAGE_NAME, value -> settings.SHOW_PACKAGE_NAME = value);
        presentationView.add(showPackageName);

        CheckBox showSourceFileName = CheckBox.create(JavaDebuggerLocalize.labelThreadsViewConfigurableShowSourceFileName());
        propertyBuilder.add(showSourceFileName, () -> settings.SHOW_SOURCE_NAME, value -> settings.SHOW_SOURCE_NAME = value);
        presentationView.add(showSourceFileName);

        CheckBox showMethodArgumentTypes = CheckBox.create(JavaDebuggerLocalize.labelThreadsViewConfigurableShowPramsTypes());
        propertyBuilder.add(showMethodArgumentTypes, () -> settings.SHOW_ARGUMENTS_TYPES, value -> settings.SHOW_ARGUMENTS_TYPES = value);
        presentationView.add(showMethodArgumentTypes);

        return rootLayout;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JavaDebuggerLocalize.threadsViewConfigurableDisplayName();
    }
}
