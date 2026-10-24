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

import com.intellij.java.debugger.impl.classFilter.UnifiedClassFilterEditor;
import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.impl.ui.PackageChooserFactory;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.java.coverage.localize.JavaCoverageLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;

import java.util.List;

/**
 * Class and package patterns of coverage data, with "Add Package" next to "Add Class".
 *
 * @author ven
 */
public class CoverageClassFilterEditor extends UnifiedClassFilterEditor {
    @RequiredUIAccess
    public CoverageClassFilterEditor(Project project) {
        super(project, aClass -> aClass.getContainingClass() == null);
    }

    @Override
    protected LocalizeValue getAddPatternButtonText() {
        return JavaCoverageLocalize.coverageButtonAddPackage();
    }

    @Override
    protected Image getAddPatternButtonIcon() {
        return PlatformIconGroup.nodesPackage();
    }

    @Override
    protected boolean addPatternButtonVisible() {
        return true;
    }

    @Override
    @RequiredUIAccess
    protected void addPatternFilter() {
        PackageChooser packageChooser = myProject.getInstance(PackageChooserFactory.class).create();
        List<PsiJavaPackage> packages = packageChooser.showAndSelect();
        if (packages != null) {
            for (PsiJavaPackage aPackage : packages) {
                String fqName = aPackage.getQualifiedName();
                addPattern(fqName.isEmpty() ? "*" : fqName + ".*");
            }
        }
    }
}
