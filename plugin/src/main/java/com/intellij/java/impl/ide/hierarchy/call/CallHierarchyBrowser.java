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
package com.intellij.java.impl.ide.hierarchy.call;

import consulo.ide.impl.idea.ide.hierarchy.CallHierarchyBrowserBase;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.java.impl.ide.hierarchy.JavaHierarchyUtil;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.PopupHandler;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.IdeActions;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

public class CallHierarchyBrowser extends CallHierarchyBrowserBase {
    private static final Logger LOG = Logger.getInstance(CallHierarchyBrowser.class);

    public CallHierarchyBrowser(@Nonnull Project project, @Nonnull PsiMethod method) {
        super(project, method);
    }

    @Override
    protected void createTrees(@Nonnull Map<String, JTree> type2TreeMap) {
        ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_CALL_HIERARCHY_POPUP);
        JTree tree1 = createTree(false);
        PopupHandler.installPopupHandler(tree1, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
        BaseOnThisMethodAction baseOnThisMethodAction = new BaseOnThisMethodAction();
        baseOnThisMethodAction
            .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree1);
        type2TreeMap.put(CALLEE_TYPE, tree1);

        JTree tree2 = createTree(false);
        PopupHandler.installPopupHandler(tree2, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
        baseOnThisMethodAction
            .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree2);
        type2TreeMap.put(CALLER_TYPE, tree2);
    }

    @Override
    protected PsiElement getElementFromDescriptor(@Nonnull HierarchyNodeDescriptor descriptor) {
        if (descriptor instanceof CallHierarchyNodeDescriptor nodeDescriptor) {
            return nodeDescriptor.getEnclosingElement();
        }
        return null;
    }

    @Override
    protected PsiElement getOpenFileElementFromDescriptor(@Nonnull HierarchyNodeDescriptor descriptor) {
        if (descriptor instanceof CallHierarchyNodeDescriptor nodeDescriptor) {
            return nodeDescriptor.getTargetElement();
        }
        return null;
    }

    @Override
    protected boolean isApplicableElement(@Nonnull PsiElement element) {
        return element instanceof PsiMethod;
    }

    @Override
    protected HierarchyTreeStructure createHierarchyTreeStructure(@Nonnull String typeName, @Nonnull PsiElement psiElement) {
        if (CALLER_TYPE.equals(typeName)) {
            return new CallerMethodsTreeStructure(myProject, (PsiMethod)psiElement, getCurrentScopeType());
        }
        else if (CALLEE_TYPE.equals(typeName)) {
            return new CalleeMethodsTreeStructure(myProject, (PsiMethod)psiElement, getCurrentScopeType());
        }
        else {
            LOG.error("unexpected type: " + typeName);
            return null;
        }
    }

    @Override
    protected Comparator<NodeDescriptor> getComparator() {
        return JavaHierarchyUtil.getComparator(myProject);
    }

    public static final class BaseOnThisMethodAction extends CallHierarchyBrowserBase.BaseOnThisMethodAction {
    }
}
