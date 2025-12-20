package com.intellij.java.impl.codeInspection.unnecessaryModuleDependency;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.GlobalInspectionTool;
import consulo.language.editor.inspection.QuickFix;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefGraphAnnotator;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.reference.RefModule;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.scope.AnalysisScope;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.orderEntry.ModuleOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author anna
 * @since 2006-01-09
 */
@ExtensionImpl
public class UnnecessaryModuleDependencyInspection extends GlobalInspectionTool {

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nullable
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Override
    public RefGraphAnnotator getAnnotator(@Nonnull RefManager refManager, @Nonnull Object state) {
        return new UnnecessaryModuleDependencyAnnotator(refManager);
    }

    @Override
    public CommonProblemDescriptor[] checkElement(
        @Nonnull RefEntity refEntity,
        @Nonnull AnalysisScope scope,
        @Nonnull InspectionManager manager,
        @Nonnull GlobalInspectionContext globalContext,
        @Nonnull Object state
    ) {
        if (refEntity instanceof RefModule) {
            RefModule refModule = (RefModule) refEntity;
            Module module = refModule.getModule();
            Module[] declaredDependencies = ModuleRootManager.getInstance(module).getDependencies();
            List<CommonProblemDescriptor> descriptors = new ArrayList<>();
            Set<Module> modules = refModule.getUserData(UnnecessaryModuleDependencyAnnotator.DEPENDENCIES);
            for (Module dependency : declaredDependencies) {
                if (modules == null || !modules.contains(dependency)) {
                    CommonProblemDescriptor problemDescriptor;
                    if (scope.containsModule(dependency)) { //external references are rejected -> annotator doesn't provide any information on them -> false positives
                        problemDescriptor = manager.createProblemDescriptor(
                            InspectionLocalize.unnecessaryModuleDependencyProblemDescriptor(module.getName(), dependency.getName()).get(),
                            new RemoveModuleDependencyFix(module, dependency)
                        );
                    }
                    else {
                        LocalizeValue message = InspectionLocalize.suspectedModuleDependencyProblemDescriptor(
                            module.getName(),
                            dependency.getName(),
                            scope.getDisplayName(),
                            dependency.getName()
                        );
                        problemDescriptor = manager.createProblemDescriptor(message.get());
                    }
                    descriptors.add(problemDescriptor);
                }
            }
            return descriptors.isEmpty() ? null : descriptors.toArray(new CommonProblemDescriptor[descriptors.size()]);
        }
        return null;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesDeclarationRedundancy();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.unnecessaryModuleDependencyDisplayName();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return "UnnecessaryModuleDependencyInspection";
    }

    public static class RemoveModuleDependencyFix implements QuickFix {
        private final Module myModule;
        private final Module myDependency;

        public RemoveModuleDependencyFix(Module module, Module dependency) {
            myModule = module;
            myDependency = dependency;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Remove dependency");
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull Project project, @Nonnull CommonProblemDescriptor descriptor) {
            ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
            for (OrderEntry entry : model.getOrderEntries()) {
                if (entry instanceof ModuleOrderEntry) {
                    Module mDependency = ((ModuleOrderEntry) entry).getModule();
                    if (Comparing.equal(mDependency, myDependency)) {
                        model.removeOrderEntry(entry);
                        break;
                    }
                }
            }
            model.commit();
        }
    }
}
