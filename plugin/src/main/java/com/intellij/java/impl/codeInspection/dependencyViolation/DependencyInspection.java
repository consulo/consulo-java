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
package com.intellij.java.impl.codeInspection.dependencyViolation;

import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataManager;
import consulo.ide.impl.idea.packageDependencies.DependenciesBuilder;
import consulo.ide.impl.idea.packageDependencies.ForwardDependenciesBuilder;
import consulo.ide.impl.idea.packageDependencies.ui.DependencyConfigurable;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.packageDependency.DependencyRule;
import consulo.language.editor.packageDependency.DependencyValidationManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author anna
 * @since 2005-02-06
 */
@ExtensionImpl
public class DependencyInspection extends BaseLocalInspectionTool {
    public static final String SHORT_NAME = "Dependency";

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    public LocalizeValue getGroupDisplayName() {
        return LocalizeValue.empty();
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.illegalPackageDependencies();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return DependencyInspection.SHORT_NAME;
    }

    @Override
    public JComponent createOptionsPanel() {
        final JButton editDependencies = new JButton(InspectionLocalize.inspectionDependencyConfigureButtonText().get());
        editDependencies.addActionListener(e -> {
            Project project = DataManager.getInstance().getDataContext(editDependencies).getData(Project.KEY);
            if (project == null) {
                project = ProjectManager.getInstance().getDefaultProject();
            }
            ShowSettingsUtil.getInstance().editConfigurable(editDependencies, new DependencyConfigurable(project));
        });

        JPanel depPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        depPanel.add(editDependencies);
        return depPanel;
    }

    @Override
    @Nullable
    public ProblemDescriptor[] checkFile(
        @Nonnull final PsiFile file,
        @Nonnull final InspectionManager manager,
        final boolean isOnTheFly,
        Object state
    ) {
        if (file == null) {
            return null;
        }
        if (file.getViewProvider().getPsi(JavaLanguage.INSTANCE) == null) {
            return null;
        }
        final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(file.getProject());
        if (!validationManager.hasRules()) {
            return null;
        }
        if (validationManager.getApplicableRules(file).length == 0) {
            return null;
        }
        final ArrayList<ProblemDescriptor> problems = new ArrayList<>();
        ForwardDependenciesBuilder builder = new ForwardDependenciesBuilder(file.getProject(), new AnalysisScope(file));
        DependenciesBuilder.analyzeFileDependencies(file, (place, dependency) -> {
            PsiFile dependencyFile = dependency.getContainingFile();
            if (dependencyFile != null && dependencyFile.isPhysical() && dependencyFile.getVirtualFile() != null) {
                final DependencyRule[] rule = validationManager.getViolatorDependencyRules(file, dependencyFile);
                for (DependencyRule dependencyRule : rule) {
                    StringBuilder message = new StringBuilder();
                    message.append(InspectionLocalize.inspectionDependencyViolatorProblemDescriptor(dependencyRule.getDisplayText()));
                    problems.add(manager.createProblemDescriptor(
                        place,
                        message.toString(),
                        isOnTheFly,
                        new LocalQuickFix[]{new EditDependencyRulesAction(dependencyRule)},
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    ));
                }
            }
        });
        return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    @Override
    @Nonnull
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

    private static class EditDependencyRulesAction implements LocalQuickFix {
        private final DependencyRule myRule;

        public EditDependencyRulesAction(DependencyRule rule) {
            myRule = rule;
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionLocalize.editDependencyRulesText(myRule.getDisplayText());
        }

        @RequiredUIAccess
        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            ShowSettingsUtil.getInstance().editConfigurable(project, new DependencyConfigurable(project));
        }
    }

}
