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
package com.intellij.java.impl.ide.hierarchy.call;

import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.hierarchy.HierarchyKind;
import consulo.language.editor.hierarchy.HierarchyModel;
import consulo.language.editor.hierarchy.HierarchyRequest;
import consulo.language.editor.hierarchy.HierarchyTreeStructure;
import consulo.language.editor.hierarchy.HierarchyViewType;
import consulo.language.editor.hierarchy.StandardHierarchyKinds;
import consulo.language.editor.hierarchy.StandardHierarchyViewTypes;
import consulo.language.editor.localize.LanguageEditorLocalize;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author VISTALL
 * @since 2026-09-19
 */
public class JavaCallHierarchyModel implements HierarchyModel<PsiMethod> {
    private static final Logger LOG = Logger.getInstance(JavaCallHierarchyModel.class);

    private final Project myProject;
    private final PsiMethod myTarget;

    public JavaCallHierarchyModel(Project project, PsiMethod target) {
        myProject = project;
        myTarget = target;
    }

    @Override
    public HierarchyKind getKind() {
        return StandardHierarchyKinds.CALL;
    }

    @Override
    public PsiMethod getTarget() {
        return myTarget;
    }

    @Override
    public List<HierarchyViewType> getViewTypes() {
        return List.of(StandardHierarchyViewTypes.CALLER, StandardHierarchyViewTypes.CALLEE);
    }

    @RequiredReadAction
    @Override
    public HierarchyViewType getDefaultViewType() {
        return StandardHierarchyViewTypes.CALLER;
    }

    @Override
    public boolean isScopeSupported() {
        return true;
    }

    @RequiredReadAction
    @Override
    public @Nullable HierarchyTreeStructure createTreeStructure(HierarchyRequest<PsiMethod> request) {
        PsiMethod target = request.getTarget();
        HierarchyViewType viewType = request.getViewType();

        if (StandardHierarchyViewTypes.CALLER.equals(viewType)) {
            return new CallerMethodsTreeStructure(myProject, target, request.getScope());
        }
        if (StandardHierarchyViewTypes.CALLEE.equals(viewType)) {
            return new CalleeMethodsTreeStructure(myProject, target, request.getScope());
        }

        LOG.error("unexpected view type: " + viewType.getId());
        return null;
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
}
