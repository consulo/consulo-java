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
import com.intellij.rt.coverage.data.ProjectData;
import consulo.execution.coverage.data.CoverageProjectData;
import consulo.execution.coverage.data.CoverageUnit;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AgentCoverageProjectData implements CoverageProjectData {
    private final ProjectData myProjectData;

    public AgentCoverageProjectData(ProjectData projectData) {
        myProjectData = projectData;
    }

    public ProjectData getProjectData() {
        return myProjectData;
    }

    @Nullable
    @Override
    public CoverageUnit getUnit(String name) {
        ClassData classData = myProjectData.getClassData(name);
        return classData == null ? null : new AgentCoverageUnit(classData);
    }

    @Override
    public CoverageUnit getOrCreateUnit(String name) {
        return new AgentCoverageUnit(myProjectData.getOrCreateClassData(name));
    }

    @Override
    public Collection<CoverageUnit> getUnits() {
        List<CoverageUnit> units = new ArrayList<>();
        for (ClassData classData : myProjectData.getClasses().values()) {
            units.add(new AgentCoverageUnit(classData));
        }
        return units;
    }

    @Override
    public void merge(CoverageProjectData data) {
        if (data instanceof AgentCoverageProjectData agentData) {
            myProjectData.merge(agentData.getProjectData());
        }
    }
}
