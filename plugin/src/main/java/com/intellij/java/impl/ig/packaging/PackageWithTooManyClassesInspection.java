/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.packaging;

import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefPackage;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.SingleIntegerFieldOptionsPanel;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class PackageWithTooManyClassesInspection extends BaseGlobalInspection {
    @SuppressWarnings({"PublicField"})
    public int limit = 10;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.packageWithTooManyClassesDisplayName();
    }

    @Override
    @Nullable
    public CommonProblemDescriptor[] checkElement(
        RefEntity refEntity,
        AnalysisScope analysisScope,
        InspectionManager inspectionManager,
        GlobalInspectionContext globalInspectionContext,
        Object state
    ) {
        if (!(refEntity instanceof RefPackage)) {
            return null;
        }
        List<RefEntity> children = refEntity.getChildren();
        if (children == null) {
            return null;
        }
        int numClasses = 0;
        for (RefEntity child : children) {
            if (child instanceof RefClass) {
                numClasses++;
            }
        }
        if (numClasses <= limit) {
            return null;
        }
        LocalizeValue errorString =
            InspectionGadgetsLocalize.packageWithTooManyClassesProblemDescriptor(refEntity.getQualifiedName(), numClasses, limit);
        return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString.get())};
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.packageWithTooManyClassesMaxOption();
        return new SingleIntegerFieldOptionsPanel(message.get(), this, "limit");
    }
}