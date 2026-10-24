/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.java.debugger.ui.classFilter.ClassFilter;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.coverage.CoverageEnabledConfiguration;
import consulo.execution.coverage.CoverageRunner;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Coverage of a Java run configuration: the patterns come from its {@link JavaCoverageIncludeFragmentState}, the runner and
 * the collecting mode from the project {@link JavaCoverageOptionsProvider}.
 *
 * @author ven
 */
public class JavaCoverageEnabledConfiguration extends CoverageEnabledConfiguration {
    private static final Logger LOG = Logger.getInstance(JavaCoverageEnabledConfiguration.class);

    private final JavaCoverageEngine myCoverageProvider;

    public JavaCoverageEnabledConfiguration(RunConfigurationBase configuration, JavaCoverageEngine coverageProvider) {
        super(configuration);
        myCoverageProvider = coverageProvider;
    }

    @Nullable
    public static JavaCoverageEnabledConfiguration getFrom(RunConfigurationBase configuration) {
        if (getOrCreate(configuration) instanceof JavaCoverageEnabledConfiguration javaCoverageEnabledConfiguration) {
            return javaCoverageEnabledConfiguration;
        }
        return null;
    }

    private JavaCoverageOptionsProvider getOptions() {
        return JavaCoverageOptionsProvider.getInstance(getConfiguration().getProject());
    }

    @Override
    public @Nullable CoverageRunner getCoverageRunner() {
        return getOptions().getCoverageRunner();
    }

    @Override
    public @Nullable String getRunnerId() {
        CoverageRunner runner = getCoverageRunner();
        return runner == null ? null : runner.getId();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSampling() {
        CoverageRunner runner = getCoverageRunner();
        return !(runner != null && runner.isCoverageByTestApplicable() && getOptions().isBranchCoverage());
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isTrackPerTestCoverage() {
        return getOptions().isTestTracking();
    }

    @Override
    public boolean isTrackTestFolders() {
        return getOptions().isTestModulesCoverage();
    }

    public void appendCoverageArgument(OwnJavaParameters javaParameters) {
        CoverageRunner runner = getCoverageRunner();
        try {
            if (runner instanceof JavaCoverageRunner javaCoverageRunner) {
                String path = getCoverageFilePath();
                assert path != null; // cannot be null here if runner != null

                javaCoverageRunner.appendCoverageArgument(
                    new File(path).getCanonicalPath(),
                    getPatterns(),
                    javaParameters,
                    isTrackPerTestCoverage() && !isSampling(),
                    isSampling()
                );
            }
        }
        catch (IOException e) {
            LOG.info(e);
        }
    }

    public JavaCoverageEngine getCoverageProvider() {
        return myCoverageProvider;
    }

    /**
     * @return the patterns, {@code null} when none is set
     */
    public ClassFilter @Nullable [] getCoveragePatterns() {
        List<JavaCoveragePatternState> patterns = JavaCoverageIncludeFragment.getState(getConfiguration()).getPatterns();
        return patterns.isEmpty() ? null : JavaCoverageIncludeFragment.toFilters(patterns);
    }

    @Nullable
    public String[] getPatterns() {
        ClassFilter[] coveragePatterns = getCoveragePatterns();
        if (coveragePatterns != null) {
            List<String> patterns = new ArrayList<>();
            for (ClassFilter coveragePattern : coveragePatterns) {
                if (coveragePattern.isEnabled()) {
                    patterns.add(coveragePattern.getPattern());
                }
            }
            return ArrayUtil.toStringArray(patterns);
        }
        return null;
    }

    public void setCoveragePatterns(ClassFilter @Nullable [] coveragePatterns) {
        JavaCoverageIncludeFragment.getState(getConfiguration())
            .setPatterns(coveragePatterns == null ? List.of() : JavaCoverageIncludeFragment.toPatterns(coveragePatterns));
    }

    @Nullable
    @Override
    public String getCoverageFilePath() {
        if (myCoverageFilePath != null) {
            return myCoverageFilePath;
        }
        myCoverageFilePath = createCoverageFile();
        return myCoverageFilePath;
    }

    public void setUpCoverageFilters(String className, String packageName) {
        if (getCoveragePatterns() == null) {
            String pattern = null;
            if (className != null && className.length() > 0) {
                int index = className.lastIndexOf('.');
                if (index >= 0) {
                    pattern = className.substring(0, index);
                }
            }
            else if (packageName != null) {
                pattern = packageName;
            }

            if (pattern != null && pattern.length() > 0) {
                setCoveragePatterns(new ClassFilter[]{new ClassFilter(pattern + ".*")});
            }
        }
    }
}
