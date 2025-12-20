/*
 * Copyright 2010-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.ui;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.util.ClassFilter;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.ListTable;
import consulo.ui.ex.awt.table.ListWrappingTableModel;
import consulo.ui.ex.awt.util.ListUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.Collection;

public class UiUtils {

  private UiUtils() {
  }

  public static void setScrollPaneSize(JScrollPane scrollPane, int rows, int columns) {
    Component view = scrollPane.getViewport().getView();
    FontMetrics fontMetrics = view.getFontMetrics(view.getFont());
    int width = fontMetrics.charWidth('m') * columns;
    scrollPane.setPreferredSize(new Dimension(width, fontMetrics.getHeight() * rows));
  }

  public static void setComponentSize(Component component, int rows, int columns) {
    FontMetrics fontMetrics = component.getFontMetrics(component.getFont());
    int width = fontMetrics.charWidth('m') * columns;
    component.setPreferredSize(new Dimension(width, fontMetrics.getHeight() * rows));
  }

  public static JPanel createAddRemovePanel(ListTable table) {
    return ToolbarDecorator.createDecorator(table)
      .setAddAction(button -> {
        ListWrappingTableModel tableModel = table.getModel();
        tableModel.addRow();
        EventQueue.invokeLater(() -> {
          int lastRowIndex = tableModel.getRowCount() - 1;
          Rectangle rectangle = table.getCellRect(lastRowIndex, 0, true);
          table.scrollRectToVisible(rectangle);
          table.editCellAt(lastRowIndex, 0);
          ListSelectionModel selectionModel = table.getSelectionModel();
          selectionModel.setSelectionInterval(lastRowIndex, lastRowIndex);
          TableCellEditor editor = table.getCellEditor();
          Component component = editor.getTableCellEditorComponent(table, null, true, lastRowIndex, 0);
          component.requestFocus();
        });
      }).setRemoveAction(new RemoveAction(table))
      .disableUpDownActions().createPanel();
  }

  public static JPanel createAddRemoveTreeClassChooserPanel(
    ListTable table,
    String chooserTitle,
    @NonNls String... ancestorClasses
  ) {
    ClassFilter filter;
    if (ancestorClasses.length == 0) {
      filter = ClassFilter.ALL;
    }
    else {
      filter = new SubclassFilter(ancestorClasses);
    }
    return ToolbarDecorator.createDecorator(table)
      .setAddAction(button -> {
        DataContext dataContext = DataManager.getInstance().getDataContext(table);
        Project project = dataContext.getData(Project.KEY);
        if (project == null) {
          return;
        }
        TreeClassChooserFactory chooserFactory = TreeClassChooserFactory.getInstance(project);
        TreeClassChooser classChooser =
          chooserFactory.createWithInnerClassesScopeChooser(chooserTitle, GlobalSearchScope.allScope(project), filter, null);
        classChooser.showDialog();
        PsiClass selectedClass = classChooser.getSelected();
        if (selectedClass == null) {
          return;
        }
        String qualifiedName = selectedClass.getQualifiedName();
        ListWrappingTableModel tableModel = table.getModel();
        int index = tableModel.indexOf(qualifiedName, 0);
        int rowIndex;
        if (index < 0) {
          tableModel.addRow(qualifiedName);
          rowIndex = tableModel.getRowCount() - 1;
        }
        else {
          rowIndex = index;
        }
        ListSelectionModel selectionModel =
          table.getSelectionModel();
        selectionModel.setSelectionInterval(rowIndex, rowIndex);
        EventQueue.invokeLater(() -> {
          Rectangle rectangle = table.getCellRect(rowIndex, 0, true);
          table.scrollRectToVisible(rectangle);
        });
      }).setRemoveAction(new RemoveAction(table))
      .disableUpDownActions().createPanel();
  }

  public static JPanel createTreeClassChooserList(
    Collection<String> collection,
    String borderTitle,
    String chooserTitle,
    String... ancestorClasses
  ) {
    ClassFilter filter;
    if (ancestorClasses.length == 0) {
      filter = ClassFilter.ALL;
    }
    else {
      filter = new SubclassFilter(ancestorClasses);
    }
    JPanel optionsPanel = new JPanel(new BorderLayout());
    JBList<String> list = new JBList<>(collection);

    JPanel panel = ToolbarDecorator.createDecorator(list)
      .disableUpDownActions()
      .setAddAction(anActionButton -> {
        DataContext dataContext = DataManager.getInstance().getDataContext(list);
        Project project = dataContext.getData(Project.KEY);
        if (project == null) {
          return;
        }
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
          .createNoInnerClassesScopeChooser(chooserTitle, GlobalSearchScope.allScope(project), filter, null);
        chooser.showDialog();
        PsiClass selected = chooser.getSelected();
        if (selected == null) {
          return;
        }
        String qualifiedName = selected.getQualifiedName();
        DefaultListModel<String> model = (DefaultListModel)list.getModel();
        int index = model.indexOf(qualifiedName);
        if (index < 0) {
          model.addElement(qualifiedName);
          collection.add(qualifiedName);
        }
        else {
          list.setSelectedIndex(index);
        }
      })
      .setRemoveAction(anActionButton -> {
        Object selectedValue = list.getSelectedValue();
        collection.remove(selectedValue);
        ListUtil.removeSelectedItems(list);
      }).createPanel();
    optionsPanel.setBorder(IdeBorderFactory.createTitledBorder(borderTitle, false, JBUI.insetsTop(10)));
    optionsPanel.add(panel);
    return optionsPanel;
  }

  private static class RemoveAction implements AnActionButtonRunnable {
    private final ListTable table;

    public RemoveAction(ListTable table) {
      this.table = table;
    }

    @Override
    public void run(AnActionButton button) {
      EventQueue.invokeLater(() -> {
        TableCellEditor editor = table.getCellEditor();
        if (editor != null) {
          editor.stopCellEditing();
        }
        ListSelectionModel selectionModel = table.getSelectionModel();
        int minIndex = selectionModel.getMinSelectionIndex();
        int maxIndex = selectionModel.getMaxSelectionIndex();
        if (minIndex == -1 || maxIndex == -1) {
          return;
        }
        ListWrappingTableModel tableModel = table.getModel();
        for (int i = minIndex; i <= maxIndex; i++) {
          if (selectionModel.isSelectedIndex(i)) {
            tableModel.removeRow(i);
          }
        }
        int count = tableModel.getRowCount();
        if (count <= minIndex) {
          selectionModel.setSelectionInterval(count - 1, count - 1);
        }
        else if (minIndex <= 0) {
          if (count > 0) {
            selectionModel.setSelectionInterval(0, 0);
          }
        }
        else {
          selectionModel.setSelectionInterval(minIndex - 1, minIndex - 1);
        }
      });
    }
  }

  private static class SubclassFilter implements ClassFilter {

    private final String[] ancestorClasses;

    private SubclassFilter(String[] ancestorClasses) {
      this.ancestorClasses = ancestorClasses;
    }

    @Override
    public boolean isAccepted(PsiClass aClass) {
      for (String ancestorClass : ancestorClasses) {
        if (InheritanceUtil.isInheritor(aClass, ancestorClass)) {
          return true;
        }
      }
      return false;
    }
  }
}
