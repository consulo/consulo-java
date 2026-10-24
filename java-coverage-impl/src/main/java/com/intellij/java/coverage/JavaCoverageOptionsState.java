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
package com.intellij.java.coverage;

import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-24
 */
public class JavaCoverageOptionsState {
    private @Nullable String myRunnerId = "idea";
    private boolean myBranchCoverage = true;
    private boolean myTestTracking;
    private boolean myTestModulesCoverage;

    public @Nullable String getRunnerId() {
        return myRunnerId;
    }

    public void setRunnerId(@Nullable String runnerId) {
        myRunnerId = runnerId;
    }

    public boolean isBranchCoverage() {
        return myBranchCoverage;
    }

    public void setBranchCoverage(boolean branchCoverage) {
        myBranchCoverage = branchCoverage;
    }

    public boolean isTestTracking() {
        return myTestTracking;
    }

    public void setTestTracking(boolean testTracking) {
        myTestTracking = testTracking;
    }

    public boolean isTestModulesCoverage() {
        return myTestModulesCoverage;
    }

    public void setTestModulesCoverage(boolean testModulesCoverage) {
        myTestModulesCoverage = testModulesCoverage;
    }
}
