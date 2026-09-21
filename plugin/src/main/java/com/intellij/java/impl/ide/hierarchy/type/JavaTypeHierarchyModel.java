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
package com.intellij.java.impl.ide.hierarchy.type;

import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
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
public class JavaTypeHierarchyModel implements HierarchyModel<PsiClass> {
    private static final Logger LOG = Logger.getInstance(JavaTypeHierarchyModel.class);

    private final Project myProject;
    private final PsiClass myTarget;

    public JavaTypeHierarchyModel(Project project, PsiClass target) {
        myProject = project;
        myTarget = target;
    }

    @Override
    public HierarchyKind getKind() {
        return StandardHierarchyKinds.TYPE;
    }

    @Override
    public PsiClass getTarget() {
        return myTarget;
    }

    @Override
    public List<HierarchyViewType> getViewTypes() {
        return List.of(
            StandardHierarchyViewTypes.CLASS,
            StandardHierarchyViewTypes.SUPERTYPES,
            StandardHierarchyViewTypes.SUBTYPES
        );
    }

    @RequiredReadAction
    @Override
    public HierarchyViewType getDefaultViewType() {
        return StandardHierarchyViewTypes.CLASS;
    }

    @RequiredReadAction
    @Override
    public HierarchyViewType correctViewType(HierarchyViewType viewType) {
        return StandardHierarchyViewTypes.CLASS.equals(viewType) && isInterface(myTarget)
            ? StandardHierarchyViewTypes.SUBTYPES
            : viewType;
    }

    @Override
    public boolean isScopeSupported() {
        return true;
    }

    @Override
    public boolean isScopeApplicable(HierarchyViewType viewType) {
        return !StandardHierarchyViewTypes.SUPERTYPES.equals(viewType);
    }

    @RequiredReadAction
    @Override
    public @Nullable HierarchyTreeStructure createTreeStructure(HierarchyRequest<PsiClass> request) {
        PsiClass target = request.getTarget();
        HierarchyViewType viewType = request.getViewType();

        if (StandardHierarchyViewTypes.SUPERTYPES.equals(viewType)) {
            return new SupertypesHierarchyTreeStructure(myProject, target);
        }
        if (StandardHierarchyViewTypes.SUBTYPES.equals(viewType)) {
            return new SubtypesHierarchyTreeStructure(myProject, target, request.getScope());
        }
        if (StandardHierarchyViewTypes.CLASS.equals(viewType)) {
            return new TypeHierarchyTreeStructure(myProject, target, request.getScope());
        }

        LOG.error("unexpected view type: " + viewType.getId());
        return null;
    }

    @RequiredReadAction
    @Override
    public boolean isApplicableElement(PsiElement element) {
        return element instanceof PsiClass;
    }

    @RequiredReadAction
    @Override
    public boolean canBeBase(PsiElement element) {
        return element instanceof PsiClass psiClass && !CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName());
    }

    @RequiredReadAction
    @Override
    public LocalizeValue getBaseOnThisText(PsiElement element) {
        return isInterface(element)
            ? LanguageEditorLocalize.actionBaseOnThisInterface()
            : LanguageEditorLocalize.actionBaseOnThisClass();
    }

    @RequiredReadAction
    @Override
    public LocalizeValue getContentDisplayName(HierarchyViewType viewType, PsiElement element) {
        return element instanceof PsiClass psiClass
            ? viewType.contentTitle(ClassPresentationUtil.getNameForClass(psiClass, false))
            : LocalizeValue.empty();
    }

    @RequiredReadAction
    private static boolean isInterface(@Nullable PsiElement element) {
        return element instanceof PsiClass psiClass && psiClass.isInterface();
    }
}
