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
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.IntBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.util.FormBuilder;

/**
 * @author VISTALL
 * @since 2026-10-05
 */
public class JfrProfilerConfigurable extends SimpleConfigurableByProperties {
    private final JfrProfilerConfigurationState myState;

    public JfrProfilerConfigurable(JfrProfilerConfigurationState state) {
        myState = state;
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        ComboBox<JfrSettingsPreset> presetBox = ComboBox.create(JfrSettingsPreset.values());
        presetBox.setTextRenderer(preset -> preset == null ? LocalizeValue.empty() : preset.getDisplayName());
        propertyBuilder.add(
            presetBox,
            myState::getPreset,
            preset -> myState.setPreset(preset == null ? JfrSettingsPreset.PROFILE : preset)
        );

        IntBox intervalBox = IntBox.create(JfrSettingsPreset.PROFILE.getExecutionSamplePeriodMs())
            .withRange(JfrProfilerConfigurationState.MIN_SAMPLING_INTERVAL_MS, JfrProfilerConfigurationState.MAX_SAMPLING_INTERVAL_MS);
        propertyBuilder.add(
            intervalBox,
            myState::getSamplingIntervalMs,
            value -> myState.setSamplingIntervalMs(value == null ? myState.getPreset().getExecutionSamplePeriodMs() : value)
        );

        CheckBox debugNonSafepointsBox = CheckBox.create(JavaProfilerLocalize.jfrSettingsDebugNonSafepoints());
        propertyBuilder.add(debugNonSafepointsBox, myState::isDebugNonSafepoints, myState::setDebugNonSafepoints);

        CheckBox nativeSamplesBox = CheckBox.create(JavaProfilerLocalize.jfrSettingsNativeSamples());
        propertyBuilder.add(nativeSamplesBox, myState::isNativeSamples, myState::setNativeSamples);

        CheckBox cpuTimeBox = CheckBox.create(JavaProfilerLocalize.jfrSettingsCpuTimeSampling());
        propertyBuilder.add(cpuTimeBox, myState::isCpuTimeSampling, myState::setCpuTimeSampling);

        FormBuilder builder = FormBuilder.create()
            .addLabeled(JavaProfilerLocalize.jfrSettingsPreset(), presetBox)
            .addLabeled(JavaProfilerLocalize.jfrSettingsSamplingInterval(), intervalBox);
        builder.addBottom(debugNonSafepointsBox);
        builder.addBottom(nativeSamplesBox);
        builder.addBottom(cpuTimeBox);
        return builder.build();
    }
}
