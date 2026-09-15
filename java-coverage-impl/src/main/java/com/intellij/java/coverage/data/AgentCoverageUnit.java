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

import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import consulo.execution.coverage.data.CoverageLine;
import consulo.execution.coverage.data.CoverageUnit;
import consulo.execution.coverage.data.LineStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AgentCoverageUnit implements CoverageUnit {
    private final ClassData myClassData;

    public AgentCoverageUnit(ClassData classData) {
        myClassData = classData;
    }

    public ClassData getClassData() {
        return myClassData;
    }

    @Override
    public String getName() {
        return myClassData.getName();
    }

    @Nullable
    @Override
    public CoverageLine getLine(int lineNumber) {
        LineData lineData = myClassData.getLineData(lineNumber);
        return lineData == null ? null : new AgentCoverageLine(lineData);
    }

    @Override
    public List<CoverageLine> getLines() {
        Object[] lines = myClassData.getLines();
        List<CoverageLine> result = new ArrayList<>();
        if (lines != null) {
            for (Object line : lines) {
                result.add(line instanceof LineData lineData ? new AgentCoverageLine(lineData) : null);
            }
        }
        return result;
    }

    @Override
    public void setLines(List<CoverageLine> lines) {
        LineData[] lineDatas = new LineData[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            CoverageLine line = lines.get(i);
            lineDatas[i] = line instanceof AgentCoverageLine agentLine ? agentLine.getLineData() : null;
        }
        myClassData.setLines(lineDatas);
    }

    @Override
    public void registerMethodSignature(CoverageLine line) {
        if (line instanceof AgentCoverageLine agentLine) {
            myClassData.registerMethodSignature(agentLine.getLineData());
        }
    }

    @Override
    public Set<String> getMethodSignatures() {
        return new LinkedHashSet<>(myClassData.getMethodSigs());
    }

    @Override
    public LineStatus getMethodStatus(String signature) {
        Integer status = myClassData.getStatus(signature);
        return status == null ? LineStatus.NOT_COVERED : AgentCoverageLine.toStatus(status);
    }

    @Nullable
    @Override
    public String getSourceFile() {
        return myClassData.getSource();
    }

    @Override
    public void setSourceFile(@Nullable String sourceFile) {
        myClassData.setSource(sourceFile);
    }
}
