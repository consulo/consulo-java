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

import consulo.application.Application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JFR settings of a {@link JfrProfilerConfigurationState}, as JMX recording settings for an attached JVM and as
 * {@code -XX:StartFlightRecording} options for a launched one.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JfrOptions {
    public static final int EVENT_OPTIONS_FEATURE_VERSION = 17;
    public static final int CPU_TIME_SAMPLE_FEATURE_VERSION = 25;

    private JfrOptions() {
    }

    /**
     * @return the name of the recordings the IDE starts, which is how a launched JVM's recording is found again
     */
    public static String recordingName() {
        return Application.get().getName().get();
    }

    public static Map<String, String> eventSettings(JfrProfilerConfigurationState state, int featureVersion, boolean linux) {
        Map<String, String> settings = new LinkedHashMap<>();
        int interval = state.getSamplingIntervalMs();
        if (interval != state.getPreset().getExecutionSamplePeriodMs()) {
            settings.put("jdk.ExecutionSample#period", interval + " ms");
        }
        if (state.isNativeSamples()) {
            settings.put("jdk.NativeMethodSample#enabled", "true");
            settings.put("jdk.NativeMethodSample#period", interval + " ms");
        }
        if (isCpuTimeSamplingSupported(state, featureVersion, linux)) {
            settings.put("jdk.CPUTimeSample#enabled", "true");
            settings.put("jdk.CPUTimeSample#throttle", interval + " ms");
        }
        return settings;
    }

    public static boolean isCpuTimeSamplingSupported(JfrProfilerConfigurationState state, int featureVersion, boolean linux) {
        return state.isCpuTimeSampling() && linux && featureVersion >= CPU_TIME_SAMPLE_FEATURE_VERSION;
    }

    public static List<String> launchVmOptions(JfrProfilerConfigurationState state, Path recordingFile, int featureVersion, boolean linux) {
        List<String> options = new ArrayList<>();
        if (state.isDebugNonSafepoints()) {
            options.add("-XX:+UnlockDiagnosticVMOptions");
            options.add("-XX:+DebugNonSafepoints");
        }

        StringBuilder recording = new StringBuilder("-XX:StartFlightRecording=");
        recording.append("name=").append(recordingName());
        recording.append(",settings=").append(state.getPreset().getJfcName());
        recording.append(",filename=").append(recordingFile.toAbsolutePath());
        recording.append(",dumponexit=true");
        if (featureVersion >= EVENT_OPTIONS_FEATURE_VERSION) {
            for (Map.Entry<String, String> entry : eventSettings(state, featureVersion, linux).entrySet()) {
                recording.append(',').append(entry.getKey()).append('=').append(entry.getValue().replace(" ", ""));
            }
        }
        options.add(recording.toString());
        return options;
    }
}
