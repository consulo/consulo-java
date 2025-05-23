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
package com.intellij.java.impl.ide.hierarchy.method;

import com.intellij.java.impl.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.java.language.psi.PsiMethod;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeBuilder;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeStructure;
import consulo.ide.impl.idea.ide.hierarchy.MethodHierarchyBrowserBase;
import consulo.ide.localize.IdeLocalize;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.awt.PopupHandler;
import consulo.ui.ex.tree.NodeDescriptor;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

public class MethodHierarchyBrowser extends MethodHierarchyBrowserBase {
    private static final Logger LOG = Logger.getInstance(MethodHierarchyBrowser.class);

    public MethodHierarchyBrowser(Project project, PsiMethod method) {
        super(project, method);
    }

    @Override
    protected void createTrees(@Nonnull Map<String, JTree> trees) {
        JTree tree = createTree(false);
        ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_METHOD_HIERARCHY_POPUP);
        PopupHandler.installPopupHandler(tree, group, ActionPlaces.METHOD_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());

        BaseOnThisMethodAction baseOnThisMethodAction = new BaseOnThisMethodAction();
        baseOnThisMethodAction
            .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_METHOD_HIERARCHY).getShortcutSet(), tree);

        trees.put(METHOD_TYPE, tree);
    }

    @Override
    protected JPanel createLegendPanel() {
        return createStandardLegendPanel(
            IdeLocalize.hierarchyLegendMethodIsDefinedInClass().get(),
            IdeLocalize.hierarchyLegendMethodDefinedInSuperclass().get(),
            IdeLocalize.hierarchyLegendMethodShouldBeDefined().get()
        );
    }

    @Override
    protected PsiElement getElementFromDescriptor(@Nonnull HierarchyNodeDescriptor descriptor) {
        return descriptor instanceof MethodHierarchyNodeDescriptor methodHierarchyNodeDescriptor
            ? methodHierarchyNodeDescriptor.getTargetElement() : null;
    }

    @Override
    protected boolean isApplicableElement(@Nonnull PsiElement psiElement) {
        return psiElement instanceof PsiMethod;
    }

    @Override
    protected HierarchyTreeStructure createHierarchyTreeStructure(@Nonnull String typeName, @Nonnull PsiElement psiElement) {
        if (!METHOD_TYPE.equals(typeName)) {
            LOG.error("unexpected type: " + typeName);
            return null;
        }
        return new MethodHierarchyTreeStructure(myProject, (PsiMethod)psiElement);
    }

    @Override
    protected Comparator<NodeDescriptor> getComparator() {
        return JavaHierarchyUtil.getComparator(myProject);
    }

    public PsiMethod getBaseMethod() {
        HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewType);
        MethodHierarchyTreeStructure treeStructure = (MethodHierarchyTreeStructure)builder.getTreeStructure();
        return treeStructure.getBaseMethod();
    }

    public static final class BaseOnThisMethodAction extends MethodHierarchyBrowserBase.BaseOnThisMethodAction {
    }

}
