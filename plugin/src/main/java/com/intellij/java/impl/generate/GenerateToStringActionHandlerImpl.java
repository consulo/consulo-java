/*
 * Copyright 2001-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.impl.generate;

import com.intellij.java.analysis.impl.generate.config.Config;
import com.intellij.java.impl.generate.template.TemplateResource;
import com.intellij.java.impl.generate.template.toString.ToStringTemplatesManager;
import com.intellij.java.impl.generate.tostring.GenerateToStringClassFilter;
import com.intellij.java.impl.generate.view.TemplatesPanel;
import com.intellij.java.language.impl.codeInsight.generation.PsiElementClassMember;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.ide.util.MemberChooser;
import consulo.ide.impl.idea.openapi.options.TabbedConfigurable;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.hint.HintManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import org.jetbrains.java.generate.GenerateToStringActionHandler;
import org.jetbrains.java.generate.GenerateToStringContext;
import org.jetbrains.java.generate.GenerateToStringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The action-handler that does the code generation.
 */
@Singleton
@ServiceImpl
public class GenerateToStringActionHandlerImpl implements GenerateToStringActionHandler, CodeInsightActionHandler {
    private static final Logger logger = Logger.getInstance("#GenerateToStringActionHandlerImpl");

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
        PsiClass clazz = getSubjectClass(editor, file);
        assert clazz != null;

        doExecuteAction(project, clazz, editor);
    }


    @Override
    public void executeActionQuickFix(Project project, PsiClass clazz) {
        doExecuteAction(project, clazz, null);
    }

    private static void doExecuteAction(@Nonnull Project project, @Nonnull PsiClass clazz, Editor editor) {
        logger.debug("+++ doExecuteAction - START +++");

        if (logger.isDebugEnabled()) {
            logger.debug("Current project " + project.getName());
        }

        PsiElementClassMember[] dialogMembers = buildMembersToShow(clazz);

        MemberChooserHeaderPanel header = new MemberChooserHeaderPanel(clazz);
        logger.debug("Displaying member chooser dialog");
        Application.get().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            MemberChooser<PsiElementClassMember> chooser = new MemberChooser<>(
                dialogMembers,
                true,
                true,
                project,
                PsiUtil.isLanguageLevel5OrHigher(clazz),
                header
            ) {
                @Nullable
                @Override
                protected String getHelpId() {
                    return "editing.altInsert.tostring";
                }
            };
            chooser.setTitle(LocalizeValue.localizeTODO("Generate toString()"));

            chooser.setCopyJavadocVisible(false);
            chooser.selectElements(dialogMembers);
            header.setChooser(chooser);
            chooser.show();

            if (DialogWrapper.OK_EXIT_CODE == chooser.getExitCode()) {
                Collection<PsiMember> selectedMembers = GenerationUtil.convertClassMembersToPsiMembers(chooser.getSelectedElements());

                TemplateResource template = header.getSelectedTemplate();
                ToStringTemplatesManager.getInstance().setDefaultTemplate(template);

                if (template.isValidTemplate()) {
                    GenerateToStringWorker.executeGenerateActionLater(
                        clazz,
                        editor,
                        selectedMembers,
                        template,
                        chooser.isInsertOverrideAnnotation()
                    );
                }
                else {
                    HintManager.getInstance().showErrorHint(editor, "toString() template '" + template.getFileName() + "' is invalid");
                }
            }
        });

        logger.debug("+++ doExecuteAction - END +++");
    }

    public static void updateDialog(PsiClass clazz, MemberChooser<PsiElementClassMember> dialog) {
        PsiElementClassMember[] members = buildMembersToShow(clazz);
        dialog.resetElements(members);
        dialog.selectElements(members);
    }

    private static PsiElementClassMember[] buildMembersToShow(PsiClass clazz) {
        Config config = GenerateToStringContext.getConfig();
        PsiField[] filteredFields = GenerateToStringUtils.filterAvailableFields(clazz, config.getFilterPattern());
        if (logger.isDebugEnabled()) {
            logger.debug("Number of fields after filtering: " + filteredFields.length);
        }
        PsiMethod[] filteredMethods;
        if (config.enableMethods) {
            // filter methods as it is enabled from config
            filteredMethods = GenerateToStringUtils.filterAvailableMethods(clazz, config.getFilterPattern());
            if (logger.isDebugEnabled()) {
                logger.debug("Number of methods after filtering: " + filteredMethods.length);
            }
        }
        else {
            filteredMethods = PsiMethod.EMPTY_ARRAY;
        }

        return GenerationUtil.combineToClassMemberList(filteredFields, filteredMethods);
    }

    @Nullable
    @RequiredReadAction
    private static PsiClass getSubjectClass(Editor editor, PsiFile file) {
        if (file == null) {
            return null;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement context = file.findElementAt(offset);

        if (context == null) {
            return null;
        }

        PsiClass clazz = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
        if (clazz == null) {
            return null;
        }

        //exclude interfaces, non-java classes etc
        return file.getApplication().getExtensionPoint(GenerateToStringClassFilter.class)
            .allMatchSafe(filter -> filter.canGenerateToString(clazz)) ? clazz : null;
    }

    public static class MemberChooserHeaderPanel extends JPanel {
        private MemberChooser<PsiElementClassMember> chooser;
        private final ComboBox<TemplateResource> comboBox;

        public void setChooser(MemberChooser chooser) {
            this.chooser = chooser;
        }

        public MemberChooserHeaderPanel(PsiClass clazz) {
            super(new GridBagLayout());

            Collection<TemplateResource> templates = ToStringTemplatesManager.getInstance().getAllTemplates();
            TemplateResource[] all = templates.toArray(new TemplateResource[templates.size()]);

            JButton settingsButton = new JButton("Settings");
            settingsButton.setMnemonic(KeyEvent.VK_S);

            comboBox = new ComboBox<>(all);
            comboBox.setRenderer(new ColoredListCellRenderer<>() {
                @Override
                protected void customizeCellRenderer(
                    @Nonnull JList<? extends TemplateResource> jList,
                    TemplateResource templateResource,
                    int i,
                    boolean b,
                    boolean b1
                ) {
                    append(templateResource.getName());
                }
            });
            settingsButton.addActionListener(new ActionListener() {
                @Override
                @RequiredUIAccess
                public void actionPerformed(ActionEvent e) {
                    TemplatesPanel ui = new TemplatesPanel(clazz.getProject());
                    Disposable disposable = Disposable.newDisposable();
                    Configurable composite = new TabbedConfigurable(disposable) {
                        @Override
                        protected List<Configurable> createConfigurables() {
                            List<Configurable> res = new ArrayList<>();
                            res.add(new GenerateToStringConfigurable(clazz.getProject()));
                            res.add(ui);
                            return res;
                        }

                        @Override
                        public String getDisplayName() {
                            return "toString() Generation Settings";
                        }

                        @Override
                        public String getHelpTopic() {
                            return "editing.altInsert.tostring.settings";
                        }

                        @Override
                        @RequiredUIAccess
                        public void apply() throws ConfigurationException {
                            super.apply();
                            updateDialog(clazz, chooser);

                            comboBox.removeAllItems();
                            for (TemplateResource resource : ToStringTemplatesManager.getInstance().getAllTemplates()) {
                                comboBox.addItem(resource);
                            }
                            comboBox.setSelectedItem(ToStringTemplatesManager.getInstance().getDefaultTemplate());
                        }
                    };

                    ShowSettingsUtil.getInstance().editConfigurable(
                        MemberChooserHeaderPanel.this,
                        composite,
                        () -> ui.selectItem(ToStringTemplatesManager.getInstance().getDefaultTemplate())
                    );
                    Disposer.dispose(disposable);
                }
            });

            comboBox.setSelectedItem(ToStringTemplatesManager.getInstance().getDefaultTemplate());

            JLabel templatesLabel = new JLabel(LocalizeValue.localizeTODO("Template: ").get());
            templatesLabel.setDisplayedMnemonic('T');
            templatesLabel.setLabelFor(comboBox);

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.anchor = GridBagConstraints.BASELINE;
            constraints.gridx = 0;
            add(templatesLabel, constraints);
            constraints.gridx = 1;
            constraints.weightx = 1.0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            add(comboBox, constraints);
            constraints.gridx = 2;
            constraints.weightx = 0.0;
            add(settingsButton, constraints);
        }

        public TemplateResource getSelectedTemplate() {
            return (TemplateResource)comboBox.getSelectedItem();
        }
    }
}
