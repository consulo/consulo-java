/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.java.impl.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiDocCommentOwner;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.colorScheme.TextAttributes;
import consulo.ide.impl.idea.ui.popup.list.ListPopupImpl;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.hint.QuestionAction;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.popup.PopupListElementRenderer;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class StaticImportMethodQuestionAction<T extends PsiMember> implements QuestionAction {
    private static final Logger LOG = Logger.getInstance(StaticImportMethodQuestionAction.class);
    private final Project myProject;
    private final Editor myEditor;
    private List<T> myCandidates;
    private final SmartPsiElementPointer<? extends PsiElement> myRef;

    public StaticImportMethodQuestionAction(Project project, Editor editor, List<T> candidates, SmartPsiElementPointer<? extends PsiElement> ref) {
        myProject = project;
        myEditor = editor;
        myCandidates = candidates;
        myRef = ref;
    }

    @Nonnull
    protected String getPopupTitle() {
        return JavaQuickFixBundle.message("method.to.import.chooser.title");
    }

    @Override
    public boolean execute() {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();

        PsiElement element = myRef.getElement();
        if (element == null || !element.isValid()) {
            return false;
        }

        for (T targetMethod : myCandidates) {
            if (!targetMethod.isValid()) {
                return false;
            }
        }

        if (myCandidates.size() == 1) {
            doImport(myCandidates.get(0));
        }
        else {
            chooseAndImport(myEditor, myProject);
        }
        return true;
    }

    private void doImport(T toImport) {
        Project project = toImport.getProject();
        PsiElement element = myRef.getElement();
        if (element == null) {
            return;
        }
        WriteCommandAction.runWriteCommandAction(project, JavaQuickFixBundle.message("add.import"), null, () -> AddSingleMemberStaticImportAction.bindAllClassRefs(element.getContainingFile(),
            toImport, toImport.getName(), toImport.getContainingClass()));
    }

    private void chooseAndImport(Editor editor, final Project project) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            doImport(myCandidates.get(0));
            return;
        }
        final BaseListPopupStep<T> step = new BaseListPopupStep<T>(getPopupTitle(), myCandidates) {

            @Override
            public boolean isAutoSelectionEnabled() {
                return false;
            }

            @Override
            public boolean isSpeedSearchEnabled() {
                return true;
            }

            @Override
            public PopupStep onChosen(T selectedValue, boolean finalChoice) {
                if (selectedValue == null) {
                    return FINAL_CHOICE;
                }

                if (finalChoice) {
                    return doFinalStep(() ->
                    {
                        PsiDocumentManager.getInstance(project).commitAllDocuments();
                        LOG.assertTrue(selectedValue.isValid());
                        doImport(selectedValue);
                    });
                }

                return AddImportAction.getExcludesStep(PsiUtil.getMemberQualifiedName(selectedValue), project);
            }

            @Override
            public boolean hasSubstep(T selectedValue) {
                return true;
            }

            @Nonnull
            @Override
            public String getTextFor(T value) {
                return getElementPresentableName(value);
            }

            @Override
            @RequiredUIAccess
            public Image getIconFor(T aValue) {
                return IconDescriptorUpdaters.getIcon(aValue, 0);
            }
        };

        ListPopupImpl popup = new ListPopupImpl(step) {
            final PopupListElementRenderer rightArrow = new PopupListElementRenderer(this);

            @Override
            protected ListCellRenderer getListElementRenderer() {
                return new PsiElementListCellRenderer<T>() {
                    public String getElementText(T element) {
                        return getElementPresentableName(element);
                    }

                    public String getContainerText(T element, String name) {
                        return PsiClassListCellRenderer.getContainerTextStatic(element);
                    }

                    public int getIconFlags() {
                        return 0;
                    }

                    @Nullable
                    @Override
                    protected TextAttributes getNavigationItemAttributes(Object value) {
                        TextAttributes attrs = super.getNavigationItemAttributes(value);
                        if (value instanceof PsiDocCommentOwner && !((PsiDocCommentOwner) value).isDeprecated()) {
                            PsiClass psiClass = ((T) value).getContainingClass();
                            if (psiClass != null && psiClass.isDeprecated()) {
                                return TextAttributes.merge(attrs, super.getNavigationItemAttributes(psiClass));
                            }
                        }
                        return attrs;
                    }

                    @Override
                    protected DefaultListCellRenderer getRightCellRenderer(Object value) {
                        final DefaultListCellRenderer moduleRenderer = super.getRightCellRenderer(value);
                        return new DefaultListCellRenderer() {
                            @Override
                            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                                JPanel panel = new JPanel(new BorderLayout());
                                if (moduleRenderer != null) {
                                    Component moduleComponent = moduleRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                                    if (!isSelected) {
                                        moduleComponent.setBackground(TargetAWT.to(getBackgroundColor(value)));
                                    }
                                    panel.add(moduleComponent, BorderLayout.CENTER);
                                }
                                rightArrow.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                                Component rightArrowComponent = rightArrow.getNextStepLabel();
                                panel.add(rightArrowComponent, BorderLayout.EAST);
                                return panel;
                            }
                        };
                    }
                };
            }
        };
        popup.showInBestPositionFor(editor);
    }

    private String getElementPresentableName(T element) {
        PsiClass aClass = element.getContainingClass();
        LOG.assertTrue(aClass != null);
        return ClassPresentationUtil.getNameForClass(aClass, false) + "." + element.getName();
    }
}


