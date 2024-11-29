/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.byteCodeViewer;

import com.intellij.java.language.psi.PsiClassOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.codeEditor.Editor;
import consulo.compiler.TranslatingCompilerFilesMonitor;
import consulo.dataContext.DataContext;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.ui.popup.NotLookupOrSearchCondition;
import consulo.ide.impl.idea.ui.popup.PopupPositionManager;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.language.editor.inject.InjectedEditorManager;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.*;
import consulo.module.content.ProjectRootManager;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author anna
 * @since 5/4/12
 */
@ActionImpl(
    id = "ByteCodeViewer",
    parents = @ActionParentRef(
        value = @ActionRef(id = "QuickActions"),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "QuickJavaDoc")
    )
)
public class ShowByteCodeAction extends AnAction {
    public ShowByteCodeAction() {
        super("Show Byte Code");
    }

    @Nullable
    @Override
    protected Image getTemplateIcon() {
        return PlatformIconGroup.filetypesBinary();
    }

    @Override
    public void update(AnActionEvent e) {
        boolean enabled = false;
        final Project project = e.getData(Project.KEY);
        if (project != null) {
            final PsiElement psiElement = getPsiElement(e.getDataContext(), project, e.getData(Editor.KEY));
            if (psiElement != null) {
                if (psiElement.getContainingFile() instanceof PsiClassOwner && ByteCodeViewerManager.getContainingClass(psiElement) != null) {
                    enabled = true;
                }
            }
        }

        e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        final DataContext dataContext = e.getDataContext();
        final Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        final Editor editor = e.getData(Editor.KEY);

        final PsiElement psiElement = getPsiElement(dataContext, project, editor);
        if (psiElement == null) {
            return;
        }

        final String psiElementTitle = ByteCodeViewerManager.getInstance(project).getTitle(psiElement);

        final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
        if (virtualFile == null) {
            return;
        }

        final SmartPsiElementPointer element = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiElement);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Searching byte code...") {
            private String myByteCode;
            private String myErrorMessage;
            private String myErrorTitle;

            @Override
            public void run(@Nonnull ProgressIndicator indicator) {
                if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(virtualFile)
                    && TranslatingCompilerFilesMonitor.getInstance().isMarkedForCompilation(project, virtualFile)) {
                    myErrorMessage = "Unable to show byte code for '" + psiElementTitle + "'. Class file does not exist or is out-of-date.";
                    myErrorTitle = "Class File Out-Of-Date";
                }
                else {
                    myByteCode = Application.get().runReadAction((Computable<String>) () -> ByteCodeViewerManager.getByteCode(psiElement));
                }
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }

                if (myErrorMessage != null && myTitle != null) {
                    Messages.showWarningDialog(project, myErrorMessage, myErrorTitle);
                    return;
                }
                final PsiElement targetElement = element.getElement();
                if (targetElement == null) {
                    return;
                }

                final ByteCodeViewerManager codeViewerManager = ByteCodeViewerManager.getInstance(project);
                if (codeViewerManager.hasActiveDockedDocWindow()) {
                    codeViewerManager.doUpdateComponent(targetElement, myByteCode);
                }
                else {
                    if (myByteCode == null) {
                        Messages.showErrorDialog(project, "Unable to parse class file for '" + psiElementTitle + "'.", "Byte Code not Found");
                        return;
                    }
                    final ByteCodeViewerComponent component = new ByteCodeViewerComponent(project, null);
                    component.setText(myByteCode, targetElement);
                    Processor<JBPopup> pinCallback = new Processor<JBPopup>() {
                        @Override
                        public boolean process(JBPopup popup) {
                            codeViewerManager.recreateToolWindow(targetElement, targetElement);
                            popup.cancel();
                            return false;
                        }
                    };

                    final JBPopup popup = JBPopupFactory.getInstance()
                        .createComponentPopupBuilder(component, null)
                        .setRequestFocusCondition(project, NotLookupOrSearchCondition.INSTANCE)
                        .setProject(project)
                        .setDimensionServiceKey(project, "ByteCodeViewer", false)
                        .setResizable(true)
                        .setMovable(true)
                        .setRequestFocus(LookupManager.getActiveLookup(editor) == null)
                        .setTitle(psiElementTitle + " Bytecode")
                        .setCouldPin(pinCallback)
                        .createPopup();
                    Disposer.register(popup, component);

                    PopupPositionManager.positionPopupInBestPosition(popup, editor, dataContext);
                }
            }
        });
    }

    @Nullable
    @RequiredReadAction
    private static PsiElement getPsiElement(DataContext dataContext, Project project, Editor editor) {
        PsiElement psiElement = null;
        if (editor == null) {
            psiElement = dataContext.getData(PsiElement.KEY);
        }
        else {
            final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
            final Editor injectedEditor = InjectedEditorManager.getInstance(project).getEditorForInjectedLanguageNoCommit(editor, file);
            if (injectedEditor != null) {
                PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(injectedEditor, project);
                psiElement = psiFile != null ? psiFile.findElementAt(injectedEditor.getCaretModel().getOffset()) : null;
            }

            if (file != null && psiElement == null) {
                psiElement = file.findElementAt(editor.getCaretModel().getOffset());
            }
        }

        return psiElement;
    }
}
