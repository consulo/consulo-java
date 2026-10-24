// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.coverage;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.execution.coverage.CoverageRunner;
import consulo.project.Project;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Project-wide Java coverage options: the runner, branch coverage (tracing), per-test tracking and coverage of test sources.
 */
@Singleton
@State(name = "JavaCoverageOptionsProvider", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class JavaCoverageOptionsProvider implements PersistentStateComponent<JavaCoverageOptionsState> {
    private final JavaCoverageOptionsState myState = new JavaCoverageOptionsState();

    public static JavaCoverageOptionsProvider getInstance(Project project) {
        return project.getInstance(JavaCoverageOptionsProvider.class);
    }

    public @Nullable CoverageRunner getCoverageRunner() {
        String runnerId = myState.getRunnerId();
        if (runnerId == null) {
            return null;
        }
        return Application.get().getExtensionPoint(CoverageRunner.class).findFirstSafe(runner -> runnerId.equals(runner.getId()));
    }

    public void setCoverageRunner(@Nullable CoverageRunner coverageRunner) {
        myState.setRunnerId(coverageRunner == null ? null : coverageRunner.getId());
    }

    public boolean isBranchCoverage() {
        return myState.isBranchCoverage();
    }

    public void setBranchCoverage(boolean branchCoverage) {
        myState.setBranchCoverage(branchCoverage);
    }

    public boolean isTestTracking() {
        return myState.isTestTracking();
    }

    public void setTestTracking(boolean testTracking) {
        myState.setTestTracking(testTracking);
    }

    public boolean isTestModulesCoverage() {
        return myState.isTestModulesCoverage();
    }

    public void setTestModulesCoverage(boolean testModulesCoverage) {
        myState.setTestModulesCoverage(testModulesCoverage);
    }

    @Override
    public JavaCoverageOptionsState getState() {
        return myState;
    }

    @Override
    public void loadState(JavaCoverageOptionsState state) {
        XmlSerializerUtil.copyBean(state, myState);
    }
}
