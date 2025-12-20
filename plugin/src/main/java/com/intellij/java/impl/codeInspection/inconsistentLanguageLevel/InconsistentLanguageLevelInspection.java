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

/*
 * User: anna
 * Date: 03-Nov-2009
 */
package com.intellij.java.impl.codeInspection.inconsistentLanguageLevel;

import com.intellij.java.impl.codeInspection.unnecessaryModuleDependency.UnnecessaryModuleDependencyInspection;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.module.EffectiveLanguageLevelUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefModule;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.orderEntry.ModuleOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.project.localize.ProjectLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

public abstract class InconsistentLanguageLevelInspection extends GlobalInspectionTool {
    private static final Logger LOGGER = Logger.getInstance(InconsistentLanguageLevelInspection.class);

    @Override
    public boolean isGraphNeeded() {
        return false;
    }

    @Override
    @RequiredReadAction
    public void runInspection(
        @Nonnull AnalysisScope scope,
        @Nonnull InspectionManager manager,
        @Nonnull GlobalInspectionContext globalContext,
        @Nonnull ProblemDescriptionsProcessor problemProcessor,
        @Nonnull Object state
    ) {
        final Set<Module> modules = new HashSet<>();
        scope.accept(new PsiElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitElement(PsiElement element) {
                Module module = ModuleUtilCore.findModuleForPsiElement(element);
                if (module != null) {
                    modules.add(module);
                }
            }
        });

        for (Module module : modules) {
            LanguageLevel languageLevel = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);

            RefModule refModule = globalContext.getRefManager().getRefModule(module);
            for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
                if (!(entry instanceof ModuleOrderEntry)) {
                    continue;
                }
                Module dependantModule = ((ModuleOrderEntry) entry).getModule();
                if (dependantModule == null) {
                    continue;
                }
                LanguageLevel dependantLanguageLevel = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(dependantModule);
                if (languageLevel.compareTo(dependantLanguageLevel) < 0) {
                    CommonProblemDescriptor problemDescriptor = manager.createProblemDescriptor(
                        "Inconsistent language level settings: module " + module.getName() + " with language level " +
                            languageLevel + " depends on module " + dependantModule.getName() + " with language level " + dependantLanguageLevel,
                        new UnnecessaryModuleDependencyInspection.RemoveModuleDependencyFix(module, dependantModule),
                        new OpenModuleSettingsFix(module)
                    );
                    problemProcessor.addProblemElement(refModule, problemDescriptor);
                }
            }
        }
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    @Nonnull
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesModularizationIssues();
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Inconsistent language level settings");
    }

    @Override
    @NonNls
    @Nonnull
    public String getShortName() {
        return "InconsistentLanguageLevel";
    }

    private static class OpenModuleSettingsFix implements QuickFix {
        private final Module myModule;

        private OpenModuleSettingsFix(Module module) {
            myModule = module;
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Open module " + myModule.getName() + " settings");
        }

        @RequiredUIAccess
        @Override
        public void applyFix(@Nonnull Project project, @Nonnull CommonProblemDescriptor descriptor) {
            if (!myModule.isDisposed()) {
                ShowSettingsUtil.getInstance().showProjectStructureDialog(
                    project,
                    projectStructureSelector ->
                        projectStructureSelector.select(myModule.getName(), ProjectLocalize.modulesClasspathTitle().get(), true)
                );
            }
        }
    }
}
