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

import consulo.execution.profiler.configuration.ProfilerConfigurationStateBase;

/**
 * @author VISTALL
 * @since 2026-10-05
 */
public class JfrProfilerConfigurationState extends ProfilerConfigurationStateBase {
    public static final int MIN_SAMPLING_INTERVAL_MS = 1;
    public static final int MAX_SAMPLING_INTERVAL_MS = 1000;

    private JfrSettingsPreset myPreset = JfrSettingsPreset.PROFILE;
    private int mySamplingIntervalMs = JfrSettingsPreset.PROFILE.getExecutionSamplePeriodMs();
    private boolean myDebugNonSafepoints = true;
    private boolean myNativeSamples;
    private boolean myCpuTimeSampling;

    @Override
    public String getConfigurationTypeId() {
        return JfrProfilerConfigurationType.ID;
    }

    public JfrSettingsPreset getPreset() {
        return myPreset;
    }

    public void setPreset(JfrSettingsPreset preset) {
        myPreset = preset;
    }

    public int getSamplingIntervalMs() {
        return mySamplingIntervalMs;
    }

    public void setSamplingIntervalMs(int samplingIntervalMs) {
        mySamplingIntervalMs = Math.clamp(samplingIntervalMs, MIN_SAMPLING_INTERVAL_MS, MAX_SAMPLING_INTERVAL_MS);
    }

    public boolean isDebugNonSafepoints() {
        return myDebugNonSafepoints;
    }

    public void setDebugNonSafepoints(boolean debugNonSafepoints) {
        myDebugNonSafepoints = debugNonSafepoints;
    }

    public boolean isNativeSamples() {
        return myNativeSamples;
    }

    public void setNativeSamples(boolean nativeSamples) {
        myNativeSamples = nativeSamples;
    }

    public boolean isCpuTimeSampling() {
        return myCpuTimeSampling;
    }

    public void setCpuTimeSampling(boolean cpuTimeSampling) {
        myCpuTimeSampling = cpuTimeSampling;
    }
}
