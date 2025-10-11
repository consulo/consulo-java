/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.deadCode;

import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.ProblemDescriptionsProcessor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.inspection.scheme.JobDescriptor;
import consulo.language.editor.scope.AnalysisScope;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author max
 */
@SuppressWarnings("ExtensionImplIsNotAnnotatedInspection")
public class DummyEntryPointsTool extends UnusedDeclarationInspection {
    @Override
    public void runInspection(
        @Nonnull AnalysisScope scope,
        @Nonnull InspectionManager manager,
        @Nonnull GlobalInspectionContext globalContext,
        @Nonnull ProblemDescriptionsProcessor problemDescriptionsProcessor,
        Object state
    ) {
    }

    @Nullable
    @Override
    public JobDescriptor[] getAdditionalJobs() {
        return JobDescriptor.EMPTY_ARRAY;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionDeadCodeEntryPointsDisplayName();
    }

    @Override
    @Nonnull
    public LocalizeValue getGroupDisplayName() {
        return LocalizeValue.of();
    }

    @Override
    @Nonnull
    public String getShortName() {
        //noinspection InspectionDescriptionNotFoundInspection
        return "";
    }
}
