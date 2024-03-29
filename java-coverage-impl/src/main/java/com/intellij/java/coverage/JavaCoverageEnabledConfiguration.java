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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nullable;

import consulo.execution.coverage.CoverageEnabledConfiguration;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.coverage.CoverageRunner;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import consulo.logging.Logger;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import com.intellij.java.debugger.ui.classFilter.ClassFilter;
import consulo.util.collection.ArrayUtil;
import consulo.java.execution.configurations.OwnJavaParameters;

/**
 * Base class for java run configurations with enabled code coverage
 * @author ven
 */
public class JavaCoverageEnabledConfiguration extends CoverageEnabledConfiguration {
  private static final Logger LOG = Logger.getInstance(JavaCoverageEnabledConfiguration.class);

  private ClassFilter[] myCoveragePatterns;

  private boolean myIsMergeWithPreviousResults = false;
  private String mySuiteToMergeWith;

  @NonNls private static final String COVERAGE_PATTERN_ELEMENT_NAME = "pattern";
  @NonNls private static final String COVERAGE_MERGE_ATTRIBUTE_NAME = "merge";
  @NonNls private static final String COVERAGE_MERGE_SUITE_ATT_NAME = "merge_suite";

  private JavaCoverageEngine myCoverageProvider;

  public JavaCoverageEnabledConfiguration(final RunConfigurationBase configuration,
                                          final JavaCoverageEngine coverageProvider) {
    super(configuration);
    myCoverageProvider = coverageProvider;
    setCoverageRunner(CoverageRunner.getInstance(IDEACoverageRunner.class));
  }

  @Nullable
  public static JavaCoverageEnabledConfiguration getFrom(final RunConfigurationBase configuration) {
    final CoverageEnabledConfiguration coverageEnabledConfiguration = getOrCreate(configuration);
    if (coverageEnabledConfiguration instanceof JavaCoverageEnabledConfiguration) {
      return (JavaCoverageEnabledConfiguration)coverageEnabledConfiguration;
    }
    return null;
  }

  public void appendCoverageArgument(final OwnJavaParameters javaParameters) {
    final CoverageRunner runner = getCoverageRunner();
    try {
      if (runner != null && runner instanceof JavaCoverageRunner) {
        final String path = getCoverageFilePath();
        assert path != null; // cannot be null here if runner != null

        ((JavaCoverageRunner)runner).appendCoverageArgument(new File(path).getCanonicalPath(),
                                                            getPatterns(),
                                                            javaParameters,
                                                            isTrackPerTestCoverage() && !isSampling(),
                                                            isSampling());
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
  public String [] getPatterns() {
    if (myCoveragePatterns != null) {
      List<String> patterns = new ArrayList<String>();
      for (ClassFilter coveragePattern : myCoveragePatterns) {
        if (coveragePattern.isEnabled()) patterns.add(coveragePattern.getPattern());
      }
      return ArrayUtil.toStringArray(patterns);
    }
    return null;
  }

  public void setCoveragePatterns(final ClassFilter[] coveragePatterns) {
    myCoveragePatterns = coveragePatterns;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    // merge with prev results
    final String mergeAttribute = element.getAttributeValue(COVERAGE_MERGE_ATTRIBUTE_NAME);
    myIsMergeWithPreviousResults = mergeAttribute != null && Boolean.valueOf(mergeAttribute).booleanValue();

    mySuiteToMergeWith = element.getAttributeValue(COVERAGE_MERGE_SUITE_ATT_NAME);

    // coverage patters
    final List children = element.getChildren(COVERAGE_PATTERN_ELEMENT_NAME);
    if (children.size() > 0) {
      myCoveragePatterns = new ClassFilter[children.size()];
      for (int i = 0; i < children.size(); i++) {
        myCoveragePatterns[i] = new ClassFilter();
        @NonNls final Element e = (Element)children.get(i);
        myCoveragePatterns[i].readExternal(e);
        final String val = e.getAttributeValue("value");
        if (val != null) {
          myCoveragePatterns[i].setPattern(val);
        }
      }
    }
  }

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
    final boolean trackPerTestCoverage = isTrackPerTestCoverage();
    if (!trackPerTestCoverage) {
      element.setAttribute(TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME, String.valueOf(trackPerTestCoverage));
    }

    // sampling
    final boolean sampling = isSampling();
    if (sampling) {
      element.setAttribute(SAMPLING_COVERAGE_ATTRIBUTE_NAME, String.valueOf(sampling));
    }

    // test folders
    final boolean trackTestFolders = isTrackTestFolders();
    if (trackTestFolders) {
      element.setAttribute(TRACK_TEST_FOLDERS, String.valueOf(trackTestFolders));
    }

    // runner
    final CoverageRunner coverageRunner = getCoverageRunner();
    final String runnerId = getRunnerId();
    if (coverageRunner != null) {
      element.setAttribute(COVERAGE_RUNNER, coverageRunner.getId());
    } else if (runnerId != null) {
      element.setAttribute(COVERAGE_RUNNER, runnerId);
    }

    // patterns
    if (myCoveragePatterns != null) {
      for (ClassFilter pattern : myCoveragePatterns) {
        @NonNls final Element patternElement = new Element(COVERAGE_PATTERN_ELEMENT_NAME);
        pattern.writeExternal(patternElement);
        element.addContent(patternElement);
      }
    }
  }

  @Nullable
  public String getCoverageFilePath() {
    if (myCoverageFilePath != null ) {
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
