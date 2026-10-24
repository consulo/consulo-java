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

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.execution.coverage.CoverageOptions;
import consulo.project.Project;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

/**
 * Adds the Java options to the project coverage settings page.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@ExtensionImpl
public class JavaCoverageOptions implements CoverageOptions {
    private final Project myProject;

    @Inject
    public JavaCoverageOptions(Project project) {
        myProject = project;
    }

    @Override
    public @Nullable Configurable createConfigurable() {
        return new JavaCoverageOptionsConfigurable(JavaCoverageOptionsProvider.getInstance(myProject));
    }
}
