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
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for java run configurations with enabled code coverage
 *
 * @author ven
 */
public class JavaCoverageEnabledConfiguration extends CoverageEnabledConfiguration {
    private static final Logger LOG = Logger.getInstance(JavaCoverageEnabledConfiguration.class);

    private ClassFilter[] myCoveragePatterns;

    private boolean myIsMergeWithPreviousResults = false;
    private String mySuiteToMergeWith;

    private static final String COVERAGE_PATTERN_ELEMENT_NAME = "pattern";
    private static final String COVERAGE_MERGE_ATTRIBUTE_NAME = "merge";
    private static final String COVERAGE_MERGE_SUITE_ATT_NAME = "merge_suite";

    private JavaCoverageEngine myCoverageProvider;

    public JavaCoverageEnabledConfiguration(RunConfigurationBase configuration, JavaCoverageEngine coverageProvider) {
        super(configuration);
        myCoverageProvider = coverageProvider;
        setCoverageRunner(CoverageRunner.getInstance(IDEACoverageRunner.class));
    }

    @Nullable
    public static JavaCoverageEnabledConfiguration getFrom(RunConfigurationBase configuration) {
        if (getOrCreate(configuration) instanceof JavaCoverageEnabledConfiguration javaCoverageEnabledConfiguration) {
            return javaCoverageEnabledConfiguration;
        }
        return null;
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


    @Nonnull
    public JavaCoverageEngine getCoverageProvider() {
        return myCoverageProvider;
    }

    public ClassFilter[] getCoveragePatterns() {
        return myCoveragePatterns;
    }

    @Nullable
    public String[] getPatterns() {
        if (myCoveragePatterns != null) {
            List<String> patterns = new ArrayList<>();
            for (ClassFilter coveragePattern : myCoveragePatterns) {
                if (coveragePattern.isEnabled()) {
                    patterns.add(coveragePattern.getPattern());
                }
            }
            return ArrayUtil.toStringArray(patterns);
        }
        return null;
    }

    public void setCoveragePatterns(ClassFilter[] coveragePatterns) {
        myCoveragePatterns = coveragePatterns;
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        super.readExternal(element);

        // merge with prev results
        String mergeAttribute = element.getAttributeValue(COVERAGE_MERGE_ATTRIBUTE_NAME);
        myIsMergeWithPreviousResults = mergeAttribute != null && Boolean.valueOf(mergeAttribute);

        mySuiteToMergeWith = element.getAttributeValue(COVERAGE_MERGE_SUITE_ATT_NAME);

        // coverage patters
        List<Element> children = element.getChildren(COVERAGE_PATTERN_ELEMENT_NAME);
        if (children.size() > 0) {
            myCoveragePatterns = new ClassFilter[children.size()];
            for (int i = 0; i < children.size(); i++) {
                myCoveragePatterns[i] = new ClassFilter();
                Element e = children.get(i);
                myCoveragePatterns[i].readExternal(e);
                String val = e.getAttributeValue("value");
                if (val != null) {
                    myCoveragePatterns[i].setPattern(val);
                }
            }
        }
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        // just for backward compatibility with settings format before "Huge Coverage Refactoring"
        // see [IDEA-56800] ProjectRunConfigurationManager component: "coverage" extension: "merge" attribute is misplaced
        // here we can't use super.writeExternal(...) due to differences in format between IDEA 10 and IDEA 9.x

        // enabled
        element.setAttribute(COVERAGE_ENABLED_ATTRIBUTE_NAME, String.valueOf(isCoverageEnabled()));

        // merge with prev
        element.setAttribute(COVERAGE_MERGE_ATTRIBUTE_NAME, String.valueOf(myIsMergeWithPreviousResults));

        if (myIsMergeWithPreviousResults && mySuiteToMergeWith != null) {
            element.setAttribute(COVERAGE_MERGE_SUITE_ATT_NAME, mySuiteToMergeWith);
        }

        // track per test
        boolean trackPerTestCoverage = isTrackPerTestCoverage();
        if (!trackPerTestCoverage) {
            element.setAttribute(TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME, String.valueOf(trackPerTestCoverage));
        }

        // sampling
        boolean sampling = isSampling();
        if (sampling) {
            element.setAttribute(SAMPLING_COVERAGE_ATTRIBUTE_NAME, String.valueOf(sampling));
        }

        // test folders
        boolean trackTestFolders = isTrackTestFolders();
        if (trackTestFolders) {
            element.setAttribute(TRACK_TEST_FOLDERS, String.valueOf(trackTestFolders));
        }

        // runner
        CoverageRunner coverageRunner = getCoverageRunner();
        String runnerId = getRunnerId();
        if (coverageRunner != null) {
            element.setAttribute(COVERAGE_RUNNER, coverageRunner.getId());
        }
        else if (runnerId != null) {
            element.setAttribute(COVERAGE_RUNNER, runnerId);
        }

        // patterns
        if (myCoveragePatterns != null) {
            for (ClassFilter pattern : myCoveragePatterns) {
                Element patternElement = new Element(COVERAGE_PATTERN_ELEMENT_NAME);
                pattern.writeExternal(patternElement);
                element.addContent(patternElement);
            }
        }
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
