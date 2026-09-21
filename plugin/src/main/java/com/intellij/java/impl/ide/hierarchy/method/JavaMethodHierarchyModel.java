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
package com.intellij.java.impl.ide.hierarchy.method;

import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.dataContext.DataSink;
import consulo.ide.localize.IdeLocalize;
import consulo.language.editor.hierarchy.HierarchyKind;
import consulo.language.editor.hierarchy.HierarchyLegendEntry;
import consulo.language.editor.hierarchy.HierarchyModel;
import consulo.language.editor.hierarchy.HierarchyNodeDescriptor;
import consulo.language.editor.hierarchy.HierarchyOption;
import consulo.language.editor.hierarchy.HierarchyRequest;
import consulo.language.editor.hierarchy.HierarchyTreeStructure;
import consulo.language.editor.hierarchy.HierarchyViewType;
import consulo.language.editor.hierarchy.StandardHierarchyKinds;
import consulo.language.editor.hierarchy.StandardHierarchyViewTypes;
import consulo.language.editor.localize.LanguageEditorLocalize;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-09-19
 */
public class JavaMethodHierarchyModel implements HierarchyModel<PsiMethod> {
    private static final Logger LOG = Logger.getInstance(JavaMethodHierarchyModel.class);

    private static final HierarchyOption HIDE_NOT_IMPLEMENTED = new HierarchyOption(
        "java.method.hierarchy.hide.not.implemented",
        LanguageEditorLocalize.actionHideNonImplementations(),
        PlatformIconGroup.gutterImplementedmethod(),
        false
    );

    private static final List<HierarchyLegendEntry> LEGEND = List.of(
        new HierarchyLegendEntry(
            PlatformIconGroup.hierarchyMethoddefined(),
            IdeLocalize.hierarchyLegendMethodIsDefinedInClass()
        ),
        new HierarchyLegendEntry(
            PlatformIconGroup.hierarchyMethodnotdefined(),
            IdeLocalize.hierarchyLegendMethodDefinedInSuperclass()
        ),
        new HierarchyLegendEntry(
            PlatformIconGroup.hierarchyShoulddefinemethod(),
            IdeLocalize.hierarchyLegendMethodShouldBeDefined()
        )
    );

    private final Project myProject;
    private final PsiMethod myTarget;

    public JavaMethodHierarchyModel(Project project, PsiMethod target) {
        myProject = project;
        myTarget = target;
    }

    @Override
    public HierarchyKind getKind() {
        return StandardHierarchyKinds.METHOD;
    }

    @Override
    public PsiMethod getTarget() {
        return myTarget;
    }

    @Override
    public List<HierarchyViewType> getViewTypes() {
        return List.of(StandardHierarchyViewTypes.METHOD);
    }

    @RequiredReadAction
    @Override
    public HierarchyViewType getDefaultViewType() {
        return StandardHierarchyViewTypes.METHOD;
    }

    @Override
    public List<HierarchyOption> getOptions() {
        return List.of(HIDE_NOT_IMPLEMENTED);
    }

    @Override
    public List<HierarchyLegendEntry> getLegend() {
        return LEGEND;
    }

    @RequiredReadAction
    @Override
    public @Nullable HierarchyTreeStructure createTreeStructure(HierarchyRequest<PsiMethod> request) {
        HierarchyViewType viewType = request.getViewType();
        if (!StandardHierarchyViewTypes.METHOD.equals(viewType)) {
            LOG.error("unexpected view type: " + viewType.getId());
            return null;
        }
        return new MethodHierarchyTreeStructure(myProject, request.getTarget(), request.isEnabled(HIDE_NOT_IMPLEMENTED));
    }

    @RequiredReadAction
    @Override
    public boolean isApplicableElement(PsiElement element) {
        return element instanceof PsiMethod;
    }

    @RequiredReadAction
    @Override
    public LocalizeValue getBaseOnThisText(PsiElement element) {
        return LanguageEditorLocalize.actionBaseOnThisMethod();
    }

    @Override
    public void uiDataSnapshot(DataSink sink, List<? extends HierarchyNodeDescriptor> selection) {
        List<HierarchyNodeDescriptor> descriptors = List.copyOf(selection);
        sink.lazy(JavaMethodHierarchySelection.KEY, () -> {
            List<PsiElement> elements = new ArrayList<>(descriptors.size());
            for (HierarchyNodeDescriptor descriptor : descriptors) {
                PsiElement element = descriptor.getPsiElement();
                if (element != null) {
                    elements.add(element);
                }
            }
            return new JavaMethodHierarchySelection(validTarget(), elements);
        });
    }

    @RequiredReadAction
    private @Nullable PsiMethod validTarget() {
        return myTarget.isValid() ? myTarget : null;
    }
}
