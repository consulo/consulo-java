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
import consulo.annotation.access.RequiredReadAction;
import consulo.dataContext.DataContext;
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
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.ReadonlyStatusHandler;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class OverrideImplementMethodAction extends AnAction implements AnActionWithSyncUpdate {
    private static final Logger LOG = Logger.getInstance(OverrideImplementMethodAction.class);

    protected OverrideImplementMethodAction(LocalizeValue text, LocalizeValue description) {
        super(text, description);
    }

    @Override
    @RequiredUIAccess
    public final void actionPerformed(AnActionEvent event) {
        DataContext dataContext = event.getDataContext();
        JavaMethodHierarchySelection selection = dataContext.getData(JavaMethodHierarchySelection.KEY);
        if (selection == null || selection.baseMethod() == null) {
            return;
        }
        Project project = dataContext.getRequiredData(Project.KEY);
        LocalizeValue commandName = event.getPresentation().getTextValue();
        CommandProcessor.getInstance().newCommand()
            .project(project)
            .name(commandName)
            .inWriteAction()
            .run(() -> {
                try {
                    List<PsiElement> selectedElements = selection.selectedElements();
                    if (!selectedElements.isEmpty()) {
                        List<VirtualFile> files = new ArrayList<>(selectedElements.size());
                        for (PsiElement selectedElement : selectedElements) {
                            PsiFile containingFile = selectedElement.getContainingFile();
                            if (containingFile != null) {
                                VirtualFile vFile = containingFile.getVirtualFile();
                                if (vFile != null) {
                                    files.add(vFile);
                                }
                            }
                        }
                        ReadonlyStatusHandler.OperationStatus status =
                            ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(VirtualFileUtil.toVirtualFileArray(files));
                        if (!status.hasReadonlyFiles()) {
                            for (PsiElement selectedElement : selectedElements) {
                                if (selectedElement instanceof PsiClass psiClass) {
                                    OverrideImplementUtil.overrideOrImplement(psiClass, selection.baseMethod());
                                }
                            }
                            ToolWindowManager.getInstance(project).activateEditorComponent();
                        }
                        else {
                            project.getApplication().invokeLater(
                                () -> Messages.showErrorDialog(project, status.getReadonlyFilesMessage().get(), commandName.get())
                            );
                        }
                    }
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            });
    }

    @Override
    public final void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        DataContext dataContext = e.getDataContext();

        JavaMethodHierarchySelection selection = dataContext.getData(JavaMethodHierarchySelection.KEY);
        if (selection == null) {
            presentation.setEnabledAndVisible(false);
            return;
        }
        if (!dataContext.hasData(Project.KEY)) {
            presentation.setEnabledAndVisible(false);
            return;
        }

        int toImplement = 0;
        int toOverride = 0;

        for (PsiElement selectedElement : selection.selectedElements()) {
            if (canImplementOverride(selectedElement, selection.baseMethod(), true)) {
                if (toOverride > 0) {
                    // no mixed actions allowed
                    presentation.setEnabledAndVisible(false);
                    return;
                }
                toImplement++;
            }
            else if (canImplementOverride(selectedElement, selection.baseMethod(), false)) {
                if (toImplement > 0) {
                    // no mixed actions allowed
                    presentation.setEnabledAndVisible(false);
                    return;
                }
                toOverride++;
            }
            else {
                // no action is applicable to this node
                presentation.setEnabledAndVisible(false);
                return;
            }
        }

        presentation.setVisible(true);

        update(presentation, toImplement, toOverride);
    }

    protected abstract void update(Presentation presentation, int toImplement, int toOverride);

    @RequiredReadAction
    private static boolean canImplementOverride(PsiElement element, @Nullable PsiMethod baseMethod, boolean toImplement) {
        if (!(element instanceof PsiClass psiClass)) {
            return false;
        }
        if (psiClass instanceof PsiSyntheticClass) {
            return false;
        }
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
