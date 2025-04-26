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

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiSyntheticClass;
import com.intellij.java.language.psi.util.MethodSignature;
import consulo.application.Application;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.MethodHierarchyBrowserBase;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.ReadonlyStatusHandler;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class OverrideImplementMethodAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(OverrideImplementMethodAction.class);

    @RequiredUIAccess
    @Override
    public final void actionPerformed(@Nonnull AnActionEvent event) {
        DataContext dataContext = event.getDataContext();
        MethodHierarchyBrowser methodHierarchyBrowser = (MethodHierarchyBrowser)dataContext.getData(MethodHierarchyBrowserBase.DATA_KEY);
        if (methodHierarchyBrowser == null) {
            return;
        }
        Project project = dataContext.getData(Project.KEY);
        if (project == null) {
            return;
        }

        LocalizeValue commandName = event.getPresentation().getTextValue();
        CommandProcessor.getInstance().newCommand()
            .project(project)
            .name(commandName)
            .inWriteAction()
            .run(() -> {
                try {
                    HierarchyNodeDescriptor[] selectedDescriptors = methodHierarchyBrowser.getSelectedDescriptors();
                    if (selectedDescriptors.length > 0) {
                        List<VirtualFile> files = new ArrayList<>(selectedDescriptors.length);
                        for (HierarchyNodeDescriptor selectedDescriptor : selectedDescriptors) {
                            PsiFile containingFile =
                                ((MethodHierarchyNodeDescriptor)selectedDescriptor).getPsiClass().getContainingFile();
                            if (containingFile != null) {
                                VirtualFile vFile = containingFile.getVirtualFile();
                                if (vFile != null) {
                                    files.add(vFile);
                                }
                            }
                        }
                        ReadonlyStatusHandler.OperationStatus status =
                            ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(VfsUtil.toVirtualFileArray(files));
                        if (!status.hasReadonlyFiles()) {
                            for (HierarchyNodeDescriptor selectedDescriptor : selectedDescriptors) {
                                PsiElement aClass = ((MethodHierarchyNodeDescriptor)selectedDescriptor).getPsiClass();
                                if (aClass instanceof PsiClass psiClass) {
                                    OverrideImplementUtil.overrideOrImplement(psiClass, methodHierarchyBrowser.getBaseMethod());
                                }
                            }
                            ToolWindowManager.getInstance(project).activateEditorComponent();
                        }
                        else {
                            Application.get().invokeLater(
                                () -> Messages.showErrorDialog(project, status.getReadonlyFilesMessage(), commandName.get())
                            );
                        }
                    }
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            });
    }

    @RequiredUIAccess
    @Override
    public final void update(@Nonnull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        DataContext dataContext = e.getDataContext();

        MethodHierarchyBrowser methodHierarchyBrowser =
            (MethodHierarchyBrowser)dataContext.getData(MethodHierarchyBrowserBase.DATA_KEY);
        if (methodHierarchyBrowser == null) {
            presentation.setEnabled(false);
            presentation.setVisible(false);
            return;
        }
        Project project = dataContext.getData(Project.KEY);
        if (project == null) {
            presentation.setEnabled(false);
            presentation.setVisible(false);
            return;
        }

        HierarchyNodeDescriptor[] selectedDescriptors = methodHierarchyBrowser.getSelectedDescriptors();
        int toImplement = 0;
        int toOverride = 0;

        for (HierarchyNodeDescriptor descriptor : selectedDescriptors) {
            if (canImplementOverride((MethodHierarchyNodeDescriptor)descriptor, methodHierarchyBrowser, true)) {
                if (toOverride > 0) {
                    // no mixed actions allowed
                    presentation.setEnabled(false);
                    presentation.setVisible(false);
                    return;
                }
                toImplement++;
            }
            else if (canImplementOverride((MethodHierarchyNodeDescriptor)descriptor, methodHierarchyBrowser, false)) {
                if (toImplement > 0) {
                    // no mixed actions allowed
                    presentation.setEnabled(false);
                    presentation.setVisible(false);
                    return;
                }
                toOverride++;
            }
            else {
                // no action is applicable to this node
                presentation.setEnabled(false);
                presentation.setVisible(false);
                return;
            }
        }

        presentation.setVisible(true);

        update(presentation, toImplement, toOverride);
    }

    protected abstract void update(Presentation presentation, int toImplement, int toOverride);

    private static boolean canImplementOverride(
        MethodHierarchyNodeDescriptor descriptor,
        MethodHierarchyBrowser methodHierarchyBrowser,
        boolean toImplement
    ) {
        PsiElement psiElement = descriptor.getPsiClass();
        if (!(psiElement instanceof PsiClass psiClass)) {
            return false;
        }
        if (psiClass instanceof PsiSyntheticClass) {
            return false;
        }
        PsiMethod baseMethod = methodHierarchyBrowser.getBaseMethod();
        if (baseMethod == null) {
            return false;
        }
        MethodSignature signature = baseMethod.getSignature(PsiSubstitutor.EMPTY);

        Collection<MethodSignature> allOriginalSignatures = toImplement
            ? OverrideImplementUtil.getMethodSignaturesToImplement(psiClass)
            : OverrideImplementUtil.getMethodSignaturesToOverride(psiClass);
        for (MethodSignature originalSignature : allOriginalSignatures) {
            if (originalSignature.equals(signature)) {
                return true;
            }
        }

        return false;
    }
}
