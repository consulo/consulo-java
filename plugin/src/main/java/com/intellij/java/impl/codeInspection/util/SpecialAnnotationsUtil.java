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

package com.intellij.java.impl.codeInspection.util;

import com.intellij.java.analysis.impl.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataManager;
import consulo.ide.impl.idea.util.IconUtil;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.*;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class SpecialAnnotationsUtil {
  public static JPanel createSpecialAnnotationsListControl(final List<String> list, final String borderTitle) {
    return createSpecialAnnotationsListControl(list, borderTitle, false);
  }

  public static JPanel createSpecialAnnotationsListControl(
    final List<String> list,
    final String borderTitle,
    final boolean acceptPatterns
  ) {
    final SortedListModel<String> listModel = new SortedListModel<>(String::compareTo);
    final JList injectionList = new JBList(listModel);
    for (String s : list) {
      listModel.add(s);
    }
    injectionList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    injectionList.getModel().addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        listChanged();
      }

      private void listChanged() {
        list.clear();
        for (int i = 0; i < listModel.getSize(); i++) {
          list.add(listModel.getElementAt(i));
        }
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        listChanged();
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        listChanged();
      }
    });

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(injectionList)
      .setAddAction(button -> {
        Project project = DataManager.getInstance().getDataContext(injectionList).getData(Project.KEY);
        if (project == null) project = ProjectManager.getInstance().getDefaultProject();
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).createWithInnerClassesScopeChooser(
          InspectionLocalize.specialAnnotationsListAnnotationClass().get(),
          GlobalSearchScope.allScope(project),
          PsiClass::isAnnotationType,
          null
        );
        chooser.showDialog();
        final PsiClass selected = chooser.getSelected();
        if (selected != null) {
          listModel.add(selected.getQualifiedName());
        }
      })
      .setAddActionName(InspectionLocalize.specialAnnotationsListAddAnnotationClass().get())
      .disableUpDownActions();

    if (acceptPatterns) {
      toolbarDecorator
        .setAddIcon(IconUtil.getAddClassIcon())
        .addExtraAction(
          new AnActionButton(
            InspectionLocalize.specialAnnotationsListAnnotationPattern(),
            LocalizeValue.empty(),
            IconUtil.getAddPatternIcon()
          ) {
            @Override
            public void actionPerformed(@Nonnull AnActionEvent e) {
              String selectedPattern = Messages.showInputDialog(
                InspectionLocalize.specialAnnotationsListAnnotationPattern().get(),
                InspectionLocalize.specialAnnotationsListAnnotationPattern().get(),
                UIUtil.getQuestionIcon()
              );
              if (selectedPattern != null) {
                listModel.add(selectedPattern);
              }
            }
          }
        )
        .setButtonComparator(
          InspectionLocalize.specialAnnotationsListAddAnnotationClass().get(),
          InspectionLocalize.specialAnnotationsListAnnotationPattern().get(),
          "Remove"
        );
    }

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(SeparatorFactory.createSeparator(borderTitle, null), BorderLayout.NORTH);
    panel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
    return panel;
  }

  public static IntentionAction createAddToSpecialAnnotationsListIntentionAction(
    final String text,
    final String family,
    final List<String> targetList,
    final String qualifiedName
  ) {
    return new SyntheticIntentionAction() {
      @Override
      @Nonnull
      public String getText() {
        return text;
      }

      public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return true;
      }

      @Override
      public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        SpecialAnnotationsUtilBase.doQuickFixInternal(project, targetList, qualifiedName);
      }

      @Override
      public boolean startInWriteAction() {
        return true;
      }
    };
  }
}
