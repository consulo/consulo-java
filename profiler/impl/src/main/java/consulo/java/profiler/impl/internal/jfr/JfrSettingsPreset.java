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
import consulo.localize.LocalizeValue;

/**
 * @author VISTALL
 * @since 2026-10-05
 */
public enum JfrSettingsPreset {
    PROFILE("profile", 10, JavaProfilerLocalize.jfrPresetProfile()),
    DEFAULT("default", 20, JavaProfilerLocalize.jfrPresetDefault());

    private final String myJfcName;
    private final int myExecutionSamplePeriodMs;
    private final LocalizeValue myDisplayName;

    JfrSettingsPreset(String jfcName, int executionSamplePeriodMs, LocalizeValue displayName) {
        myJfcName = jfcName;
        myExecutionSamplePeriodMs = executionSamplePeriodMs;
        myDisplayName = displayName;
    }

    public String getJfcName() {
        return myJfcName;
    }

    public int getExecutionSamplePeriodMs() {
        return myExecutionSamplePeriodMs;
    }

    public LocalizeValue getDisplayName() {
        return myDisplayName;
    }
}
