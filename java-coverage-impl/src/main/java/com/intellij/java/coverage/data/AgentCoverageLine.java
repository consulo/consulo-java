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
package com.intellij.java.coverage.data;

import com.intellij.rt.coverage.data.JumpData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.SwitchData;
import consulo.execution.coverage.data.BranchCoverage;
import consulo.execution.coverage.data.BranchCoverageImpl;
import consulo.execution.coverage.data.CoverageLine;
import consulo.execution.coverage.data.LineStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AgentCoverageLine implements CoverageLine {
    private final LineData myLineData;

    public AgentCoverageLine(LineData lineData) {
        myLineData = lineData;
    }

    public LineData getLineData() {
        return myLineData;
    }

    static LineStatus toStatus(int status) {
        return switch (status) {
            case LineCoverage.FULL -> LineStatus.COVERED;
            case LineCoverage.PARTIAL -> LineStatus.PARTIALLY_COVERED;
            default -> LineStatus.NOT_COVERED;
        };
    }

    static byte toAgentStatus(LineStatus status) {
        return switch (status) {
            case COVERED -> LineCoverage.FULL;
            case PARTIALLY_COVERED -> LineCoverage.PARTIAL;
            case NOT_COVERED -> LineCoverage.NONE;
        };
    }

    @Override
    public int getLineNumber() {
        return myLineData.getLineNumber();
    }

    @Override
    public LineStatus getStatus() {
        return toStatus(myLineData.getStatus());
    }

    @Override
    public void setStatus(LineStatus status) {
        myLineData.setStatus(toAgentStatus(status));
    }

    @Override
    public int getHits() {
        return myLineData.getHits();
    }

    @Nullable
    @Override
    public String getMethodSignature() {
        return myLineData.getMethodSignature();
    }

    @Override
    public boolean isCoveredBySingleTest() {
        return myLineData.isCoveredByOneTest();
    }

    @Nullable
    @Override
    public String getUniqueTestName() {
        return myLineData.isCoveredByOneTest() ? "" : null;
    }

    @Override
    public List<BranchCoverage> getBranches() {
        List<BranchCoverage> branches = new ArrayList<>();
        JumpData[] jumps = myLineData.getJumps();
        if (jumps != null) {
            for (JumpData jump : jumps) {
                BranchCoverageImpl branch = new BranchCoverageImpl(2);
                for (int i = 0; i < jump.getTrueHits(); i++) {
                    branch.touch(0);
                }
                for (int i = 0; i < jump.getFalseHits(); i++) {
                    branch.touch(1);
                }
                branches.add(branch);
            }
        }
        SwitchData[] switches = myLineData.getSwitches();
        if (switches != null) {
            for (SwitchData switchData : switches) {
                int[] hits = switchData.getHits();
                BranchCoverageImpl branch = new BranchCoverageImpl(hits.length);
                for (int i = 0; i < hits.length; i++) {
                    for (int h = 0; h < hits[i]; h++) {
                        branch.touch(i);
                    }
                }
                branches.add(branch);
            }
        }
        return branches;
    }
}
