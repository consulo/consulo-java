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
package com.intellij.java.impl.ide.favoritesTreeView.smartPointerPsiNodes;

import com.intellij.java.language.psi.PsiDocCommentOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.CodeInsightColors;
import consulo.component.util.Iconable;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.*;
import consulo.logging.Logger;
import consulo.navigation.NavigationItem;
import consulo.project.Project;
import consulo.project.ui.view.tree.*;
import consulo.ui.ex.tree.PresentationData;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;

public abstract class BaseSmartPointerPsiNode<Type extends SmartPsiElementPointer> extends ProjectViewNode<Type> implements
    PsiElementNavigationItem {
    private static final Logger LOG = Logger.getInstance(BaseSmartPointerPsiNode.class);

    protected BaseSmartPointerPsiNode(Project project, Type value, ViewSettings viewSettings) {
        super(project, value, viewSettings);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public final Collection<AbstractTreeNode> getChildren() {
        PsiElement value = getPsiElement();
        if (value == null) {
            return new ArrayList<>();
        }
        LOG.assertTrue(value.isValid());
        return getChildrenImpl();
    }

    @Nonnull
    protected abstract Collection<AbstractTreeNode> getChildrenImpl();

    protected boolean isMarkReadOnly() {
        Object parentValue = getParentValue();
        return parentValue instanceof PsiDirectory || parentValue instanceof PackageElement;
    }

    @Override
    @RequiredReadAction
    public PsiElement getTargetElement() {
        VirtualFile file = getVirtualFileForValue();
        if (file == null) {
            return null;
        }
        else {
            return file.isDirectory()
                ? PsiManager.getInstance(getProject()).findDirectory(file)
                : PsiManager.getInstance(getProject()).findFile(file);
        }
    }

    private VirtualFile getVirtualFileForValue() {
        PsiElement value = getPsiElement();
        if (value == null) {
            return null;
        }
        return PsiUtilCore.getVirtualFile(value);
    }
    // Should be called in atomic action

    protected abstract void updateImpl(PresentationData data);

    @Override
    @RequiredReadAction
    public void update(PresentationData data) {
        PsiElement value = getPsiElement();
        if (value == null || !value.isValid()) {
            setValue(null);
        }
        if (getPsiElement() == null) {
            return;
        }

        int flags = Iconable.ICON_FLAG_VISIBILITY;
        if (isMarkReadOnly()) {
            flags |= Iconable.ICON_FLAG_READ_STATUS;
        }

        LOG.assertTrue(value.isValid());

        Image icon = IconDescriptorUpdaters.getIcon(value, flags);
        data.setIcon(icon);
        data.setPresentableText(myName);
        if (isDeprecated()) {
            data.setAttributesKey(CodeInsightColors.DEPRECATED_ATTRIBUTES);
        }
        updateImpl(data);
        for (ProjectViewNodeDecorator decorator : ProjectViewNodeDecorator.EP_NAME.getExtensionList(myProject)) {
            decorator.decorate(this, data);
        }
    }

    private boolean isDeprecated() {
        PsiElement element = getPsiElement();
        return element instanceof PsiDocCommentOwner docCommentOwner && element.isValid() && docCommentOwner.isDeprecated();
    }

    @Override
    public boolean contains(@Nonnull VirtualFile file) {
        if (getPsiElement() == null) {
            return false;
        }
        PsiFile containingFile = getPsiElement().getContainingFile();
        return file.equals(containingFile.getVirtualFile());
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (canNavigate()) {
            ((NavigationItem)getPsiElement()).navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return getPsiElement() instanceof NavigationItem && ((NavigationItem)getPsiElement()).canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return getPsiElement() instanceof NavigationItem && ((NavigationItem)getPsiElement()).canNavigateToSource();
    }

    protected PsiElement getPsiElement() {
        //noinspection CastToIncompatibleInterface
        return (PsiElement)getValue(); // automatically de-anchorized in AbstractTreeNode.getValue
    }
}
