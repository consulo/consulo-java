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

import com.intellij.java.debugger.ui.classFilter.ClassFilter;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentGroup;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.execution.coverage.CoverageEngine;
import consulo.java.coverage.localize.JavaCoverageLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import consulo.util.xml.serializer.InvalidDataException;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "Specify classes and packages" of the "Code Coverage" group: the classes and packages a Java run configuration records
 * coverage for.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@ExtensionImpl
public class JavaCoverageIncludeFragment implements RunConfigurationFragment<RunConfiguration> {
    private static final String LEGACY_EXTENSION_ID = "coverage";
    private static final String LEGACY_PATTERN = "pattern";

    @Override
    public String getId() {
        return "coverage.include";
    }

    @Override
    public LocalizeValue getName() {
        return JavaCoverageLocalize.coverageSettingsInclude();
    }

    @Override
    public @Nullable String getParentId() {
        return StandardRunConfigurationFragmentGroup.COVERAGE;
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.COVERAGE + 10;
    }

    @Override
    public boolean isApplicable(RunConfiguration configuration) {
        return configuration instanceof RunConfigurationBase base
            && Application.get().getExtensionPoint(CoverageEngine.class).findExtensionOrFail(JavaCoverageEngine.class).isApplicableTo(base);
    }

    @Override
    public boolean isVisibleByDefault(RunConfiguration target) {
        return !getState(target).getPatterns().isEmpty();
    }

    @Override
    public void applyHidden(RunConfiguration target) {
        getState(target).setPatterns(List.of());
    }

    @Override
    public Class<JavaCoverageIncludeFragmentState> getStateClass() {
        return JavaCoverageIncludeFragmentState.class;
    }

    @Override
    public void convertLegacyState(RunConfiguration configuration, LegacyRunConfigurationConverter converter) {
        Element extension = converter.findExtension(LEGACY_EXTENSION_ID);
        if (extension == null) {
            return;
        }

        List<JavaCoveragePatternState> patterns = new ArrayList<>();
        for (Element patternElement : extension.getChildren(LEGACY_PATTERN)) {
            ClassFilter filter = new ClassFilter();
            try {
                filter.readExternal(patternElement);
            }
            catch (InvalidDataException ignored) {
            }
            String value = patternElement.getAttributeValue("value");
            if (value != null) {
                filter.setPattern(value);
            }
            if (!filter.getPattern().isEmpty()) {
                patterns.add(new JavaCoveragePatternState(filter.getPattern(), filter.isEnabled()));
            }
        }

        if (!patterns.isEmpty()) {
            JavaCoverageIncludeFragmentState state = new JavaCoverageIncludeFragmentState();
            state.setPatterns(patterns);
            converter.addFragment(state);
        }
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<RunConfiguration> createEditor(Project project, Disposable parent) {
        CoverageClassFilterEditor filterEditor = new CoverageClassFilterEditor(project);

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addBottom(LabeledLayout.create(JavaCoverageLocalize.recordCoverageFiltersTitle(), filterEditor.getComponent()));
            }

            @RequiredUIAccess
            @Override
            public void reset(RunConfiguration target) {
                filterEditor.setFilters(toFilters(getState(target).getPatterns()));
            }

            @RequiredUIAccess
            @Override
            public void apply(RunConfiguration target) {
                getState(target).setPatterns(toPatterns(filterEditor.getFilters()));
            }
        };
    }

    static ClassFilter[] toFilters(List<JavaCoveragePatternState> patterns) {
        ClassFilter[] filters = new ClassFilter[patterns.size()];
        for (int i = 0; i < filters.length; i++) {
            JavaCoveragePatternState pattern = patterns.get(i);
            filters[i] = new ClassFilter(pattern.getPattern());
            filters[i].setEnabled(pattern.isEnabled());
        }
        return filters;
    }

    static List<JavaCoveragePatternState> toPatterns(ClassFilter[] filters) {
        List<JavaCoveragePatternState> patterns = new ArrayList<>(filters.length);
        for (ClassFilter filter : filters) {
            patterns.add(new JavaCoveragePatternState(filter.getPattern(), filter.isEnabled()));
        }
        return patterns;
    }

    static JavaCoverageIncludeFragmentState getState(RunConfiguration configuration) {
        return ((RunConfigurationBase) configuration).getFragmentState(JavaCoverageIncludeFragmentState.class);
    }
}
