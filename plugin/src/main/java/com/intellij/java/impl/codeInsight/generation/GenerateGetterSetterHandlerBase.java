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
package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.impl.generate.exception.GenerateCodeException;
import com.intellij.java.impl.generate.template.TemplateResource;
import com.intellij.java.impl.generate.template.TemplatesManager;
import com.intellij.java.impl.generate.view.TemplatesPanel;
import com.intellij.java.language.impl.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.java.language.psi.PsiClass;
import consulo.codeEditor.Editor;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.generation.ClassMember;
import consulo.language.editor.hint.HintManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.ComponentWithBrowseButton;
import consulo.ui.ex.awt.ListCellRendererWrapper;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public abstract class GenerateGetterSetterHandlerBase extends GenerateMembersHandlerBase {
    private static final Logger LOG = Logger.getInstance(GenerateGetterSetterHandlerBase.class);

    public GenerateGetterSetterHandlerBase(@Nonnull LocalizeValue chooserTitle) {
        super(chooserTitle);
    }

    @Override
    protected boolean hasMembers(@Nonnull PsiClass aClass) {
        return !GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass).isEmpty();
    }

    @Override
    protected String getHelpId() {
        return "Getter and Setter Templates Dialog";
    }

    @Override
    @RequiredUIAccess
    protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project, Editor editor) {
        ClassMember[] allMembers = getAllOriginalMembers(aClass);
        if (allMembers == null) {
            HintManager.getInstance().showErrorHint(editor, getNothingFoundMessage());
            return null;
        }
        if (allMembers.length == 0) {
            HintManager.getInstance().showErrorHint(editor, getNothingAcceptedMessage());
            return null;
        }
        return chooseMembers(allMembers, false, false, project, editor);
    }

    protected static JComponent getHeaderPanel(
        final Project project,
        final TemplatesManager templatesManager,
        final String templatesTitle
    ) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel templateChooserLabel = new JLabel(templatesTitle);
        panel.add(templateChooserLabel, BorderLayout.WEST);
        ComboBox<TemplateResource> comboBox = new ComboBox<>();
        templateChooserLabel.setLabelFor(comboBox);
        comboBox.setRenderer(new ListCellRendererWrapper<>() {
            @Override
            public void customize(JList list, TemplateResource value, int index, boolean selected, boolean hasFocus) {
                setText(value.getName());
            }
        });
        ComponentWithBrowseButton<ComboBox> comboBoxWithBrowseButton = new ComponentWithBrowseButton<ComboBox>(
            comboBox,
            e -> {
                TemplatesPanel ui = new TemplatesPanel(project, templatesManager) {
                    @Override
                    protected boolean onMultipleFields() {
                        return false;
                    }

                    @Override
                    public LocalizeValue getDisplayName() {
                        return LocalizeValue.localizeTODO(StringUtil.capitalizeWords(
                            UIUtil.removeMnemonic(StringUtil.trimEnd(templatesTitle, ":")),
                            true
                        ));
                    }
                };
                ui.selectNodeInTree(templatesManager.getDefaultTemplate());
                ShowSettingsUtil.getInstance().editConfigurable(panel, ui).doWhenDone(() -> setComboBoxModel(templatesManager, comboBox));
            }
        );

        setComboBoxModel(templatesManager, comboBox);
        comboBox.addActionListener(M -> templatesManager.setDefaultTemplate((TemplateResource) comboBox.getSelectedItem()));

        panel.add(comboBoxWithBrowseButton, BorderLayout.CENTER);
        return panel;
    }

    private static void setComboBoxModel(TemplatesManager templatesManager, ComboBox<TemplateResource> comboBox) {
        Collection<TemplateResource> templates = templatesManager.getAllTemplates();
        comboBox.setModel(new DefaultComboBoxModel<>(templates.toArray(new TemplateResource[templates.size()])));
        comboBox.setSelectedItem(templatesManager.getDefaultTemplate());
    }

    @Override
    protected abstract String getNothingFoundMessage();

    protected abstract String getNothingAcceptedMessage();

    public boolean canBeAppliedTo(PsiClass targetClass) {
        ClassMember[] allMembers = getAllOriginalMembers(targetClass);
        return allMembers != null && allMembers.length != 0;
    }

    @Override
    @Nullable
    protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
        List<EncapsulatableClassMember> list = GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass);
        if (list.isEmpty()) {
            return null;
        }
        List<EncapsulatableClassMember> members = ContainerUtil.findAll(
            list,
            member -> {
                try {
                    return generateMemberPrototypes(aClass, member).length > 0;
                }
                catch (GenerateCodeException e) {
                    return true;
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                    return false;
                }
            }
        );
        return members.toArray(new ClassMember[members.size()]);
    }
}
