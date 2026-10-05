/*
 * Copyright 2013-2026 consulo.io
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
package consulo.java.profiler.impl.internal.jfr;

import consulo.java.profiler.localize.JavaProfilerLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.concurrent.ApplicationConcurrency;
import consulo.configurable.UnnamedConfigurable;
import consulo.execution.profiler.configuration.ProfilerAttacher;
import consulo.execution.profiler.configuration.ProfilerConfigurationTypeBase;
import consulo.execution.profiler.configuration.ProfilerStarter;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import jakarta.inject.Inject;

/**
 * Profiles JVMs with Java Flight Recorder: live JMX charts and JFR CPU recordings, attached or launched.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
@ExtensionImpl
public class JfrProfilerConfigurationType extends ProfilerConfigurationTypeBase<JfrProfilerConfigurationState> {
    public static final String ID = "java.jfr";
    public static final String LANGUAGE_SETTINGS_GROUP = "profiler.java";

    private static final LocalizeValue DISPLAY_NAME = JavaProfilerLocalize.jfrName();

    private final ApplicationConcurrency myConcurrency;

    @Inject
    public JfrProfilerConfigurationType(ApplicationConcurrency concurrency) {
        myConcurrency = concurrency;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Image getIcon() {
        return JavaPsiImplIconGroup.java();
    }

    @Override
    public String getLanguageSettingsGroup() {
        return LANGUAGE_SETTINGS_GROUP;
    }

    @Override
    public JfrProfilerConfigurationState getTemplateState() {
        JfrProfilerConfigurationState state = new JfrProfilerConfigurationState();
        state.setDisplayName(DISPLAY_NAME.get());
        return state;
    }

    @Override
    public UnnamedConfigurable createConfigurable(JfrProfilerConfigurationState state) {
        return new JfrProfilerConfigurable(state);
    }

    @Override
    public ProfilerStarter createStarter(JfrProfilerConfigurationState state) {
        return new JavaProfilerStarter();
    }

    @Override
    public ProfilerAttacher createAttacher(JfrProfilerConfigurationState state) {
        return new JvmProfilerAttacher(myConcurrency, state);
    }
}
