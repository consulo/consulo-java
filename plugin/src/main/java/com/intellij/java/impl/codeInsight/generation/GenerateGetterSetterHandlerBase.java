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

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiField;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.util.NotNullFunction;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.generation.ClassMember;
import consulo.language.editor.hint.HintManager;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.ComponentWithBrowseButton;
import consulo.ui.ex.awt.ListCellRendererWrapper;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import org.jetbrains.annotations.Nls;
import com.intellij.java.impl.generate.exception.GenerateCodeException;
import com.intellij.java.impl.generate.template.TemplateResource;
import com.intellij.java.impl.generate.template.TemplatesManager;
import com.intellij.java.impl.generate.view.TemplatesPanel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class GenerateGetterSetterHandlerBase extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance(GenerateGetterSetterHandlerBase.class);

  static {
    GenerateAccessorProviderRegistrar.registerProvider(new NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>() {
      @Override
      @Nonnull
      public Collection<EncapsulatableClassMember> apply(PsiClass s) {
        if (s.getLanguage() != JavaLanguage.INSTANCE) {
          return Collections.emptyList();
        }
        final List<EncapsulatableClassMember> result = new ArrayList<EncapsulatableClassMember>();
        for (PsiField field : s.getFields()) {
          if (!(field instanceof PsiEnumConstant)) {
            result.add(new PsiFieldMember(field));
          }
        }
        return result;
      }
    });
  }

  public GenerateGetterSetterHandlerBase(String chooserTitle) {
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
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project, Editor editor) {
    final ClassMember[] allMembers = getAllOriginalMembers(aClass);
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

  protected static JComponent getHeaderPanel(final Project project, final TemplatesManager templatesManager, final String templatesTitle) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JLabel templateChooserLabel = new JLabel(templatesTitle);
    panel.add(templateChooserLabel, BorderLayout.WEST);
    final ComboBox comboBox = new ComboBox();
    templateChooserLabel.setLabelFor(comboBox);
    comboBox.setRenderer(new ListCellRendererWrapper<TemplateResource>() {
      @Override
      public void customize(JList list, TemplateResource value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
      }
    });
    final ComponentWithBrowseButton<ComboBox> comboBoxWithBrowseButton = new ComponentWithBrowseButton<ComboBox>(comboBox, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final TemplatesPanel ui = new TemplatesPanel(project, templatesManager) {
          @Override
          protected boolean onMultipleFields() {
            return false;
          }

          @Nls
          @Override
          public String getDisplayName() {
            return StringUtil.capitalizeWords(UIUtil.removeMnemonic(StringUtil.trimEnd(templatesTitle, ":")), true);
          }
        };
        ui.selectNodeInTree(templatesManager.getDefaultTemplate());
        ShowSettingsUtil.getInstance().editConfigurable(panel, ui).doWhenDone(() -> setComboboxModel(templatesManager, comboBox));
      }
    });

    setComboboxModel(templatesManager, comboBox);
    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@Nonnull final ActionEvent M) {
        templatesManager.setDefaultTemplate((TemplateResource) comboBox.getSelectedItem());
      }
    });

    panel.add(comboBoxWithBrowseButton, BorderLayout.CENTER);
    return panel;
  }

  private static void setComboboxModel(TemplatesManager templatesManager, ComboBox comboBox) {
    final Collection<TemplateResource> templates = templatesManager.getAllTemplates();
    comboBox.setModel(new DefaultComboBoxModel(templates.toArray(new TemplateResource[templates.size()])));
    comboBox.setSelectedItem(templatesManager.getDefaultTemplate());
  }

  @Override
  protected abstract String getNothingFoundMessage();

  protected abstract String getNothingAcceptedMessage();

  public boolean canBeAppliedTo(PsiClass targetClass) {
    final ClassMember[] allMembers = getAllOriginalMembers(targetClass);
    return allMembers != null && allMembers.length != 0;
  }

  @Override
  @Nullable
  protected ClassMember[] getAllOriginalMembers(final PsiClass aClass) {
    final List<EncapsulatableClassMember> list = GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass);
    if (list.isEmpty()) {
      return null;
    }
    final List<EncapsulatableClassMember> members = ContainerUtil.findAll(list, new Condition<EncapsulatableClassMember>() {
      @Override
      public boolean value(EncapsulatableClassMember member) {
        try {
          return generateMemberPrototypes(aClass, member).length > 0;
        } catch (GenerateCodeException e) {
          return true;
        } catch (IncorrectOperationException e) {
          LOG.error(e);
          return false;
        }
      }
    });
    return members.toArray(new ClassMember[members.size()]);
  }


}
