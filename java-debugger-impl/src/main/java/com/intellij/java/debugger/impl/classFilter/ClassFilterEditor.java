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

/*
 * Class ClassFilterEditor
 * @author Jeka
 */
package com.intellij.java.debugger.impl.classFilter;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.util.ClassFilter;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.ui.ex.localize.UILocalize;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ClassFilterEditor extends JPanel implements ComponentWithEmptyText {
  protected JBTable myTable = null;
  protected FilterTableModel myTableModel = null;
  protected final Project myProject;
  private final ClassFilter myChooserFilter;
  @Nullable
  private final String myPatternsHelpId;

  public ClassFilterEditor(Project project) {
    this(project, null);
  }

  public ClassFilterEditor(Project project, ClassFilter classFilter) {
    this(project, classFilter, null);
  }

  public ClassFilterEditor(Project project, ClassFilter classFilter, @Nullable String patternsHelpId) {
    super(new BorderLayout());
    myPatternsHelpId = patternsHelpId;
    myTable = new JBTable();

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTable)
      .addExtraAction(new AnActionButton(getAddButtonText(), getAddButtonIcon()) {
        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
          addClassFilter();
        }

        @Override
        public void updateButton(AnActionEvent e) {
          super.updateButton(e);
          setEnabled(!myProject.isDefault());
        }
      });
    if (addPatternButtonVisible()) {
      decorator.addExtraAction(new AnActionButton(getAddPatternButtonText(), getAddPatternButtonIcon()) {
        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
          addPatternFilter();
        }

        @Override
        public void updateButton(AnActionEvent e) {
          super.updateButton(e);
          setEnabled(!myProject.isDefault());
        }
      });
    }
    add(
      decorator.setRemoveAction(button -> TableUtil.removeSelectedItems(myTable))
        .setButtonComparator(getAddButtonText(), getAddPatternButtonText(), "Remove")
        .disableUpDownActions()
        .createPanel(),
      BorderLayout.CENTER
    );

    myChooserFilter = classFilter;
    myProject = project;

    myTableModel = new FilterTableModel();
    myTable.setModel(myTableModel);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setTableHeader(null);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    myTable.setPreferredScrollableViewportSize(new Dimension(200, 100));

    TableColumnModel columnModel = myTable.getColumnModel();
    TableColumn column = columnModel.getColumn(FilterTableModel.CHECK_MARK);
    int width = new JCheckBox().getPreferredSize().width;
    column.setPreferredWidth(width);
    column.setMaxWidth(width);
    column.setCellRenderer(new EnabledCellRenderer(myTable.getDefaultRenderer(Boolean.class)));
    columnModel.getColumn(FilterTableModel.FILTER).setCellRenderer(new FilterCellRenderer());

    getEmptyText().setText(UILocalize.noPatterns().get());
  }

  @Nonnull
  @Override
  public StatusText getEmptyText() {
    return myTable.getEmptyText();
  }

  protected String getAddButtonText() {
    return UILocalize.buttonAddClass().get();
  }

  protected String getAddPatternButtonText() {
    return UILocalize.buttonAddPattern().get();
  }

  protected Image getAddButtonIcon() {
    return PlatformIconGroup.toolbardecoratorAddclass();
  }

  protected Image getAddPatternButtonIcon() {
    return PlatformIconGroup.toolbardecoratorAddpattern();
  }

  protected boolean addPatternButtonVisible() {
    return true;
  }

  public void setFilters(com.intellij.java.debugger.ui.classFilter.ClassFilter[] filters) {
    myTableModel.setFilters(filters);
  }

  public com.intellij.java.debugger.ui.classFilter.ClassFilter[] getFilters() {
    return myTableModel.getFilters();
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myTable.setEnabled(enabled);
    myTable.setRowSelectionAllowed(enabled);
    myTableModel.fireTableDataChanged();
  }

  public void stopEditing() {
    TableCellEditor editor = myTable.getCellEditor();
    if (editor != null) {
      editor.stopCellEditing();
    }
  }

  protected final class FilterTableModel extends AbstractTableModel implements ItemRemovable {
    private final List<com.intellij.java.debugger.ui.classFilter.ClassFilter> myFilters = new LinkedList<>();
    public static final int CHECK_MARK = 0;
    public static final int FILTER = 1;

    public final void setFilters(com.intellij.java.debugger.ui.classFilter.ClassFilter[] filters) {
      myFilters.clear();
      if (filters != null) {
        ContainerUtil.addAll(myFilters, filters);
      }
      fireTableDataChanged();
    }

    public com.intellij.java.debugger.ui.classFilter.ClassFilter[] getFilters() {
      for (Iterator<com.intellij.java.debugger.ui.classFilter.ClassFilter> it = myFilters.iterator(); it.hasNext(); ) {
        com.intellij.java.debugger.ui.classFilter.ClassFilter filter = it.next();
        String pattern = filter.getPattern();
        if (pattern == null || "".equals(pattern)) {
          it.remove();
        }
      }
      return myFilters.toArray(new com.intellij.java.debugger.ui.classFilter.ClassFilter[myFilters.size()]);
    }

    public com.intellij.java.debugger.ui.classFilter.ClassFilter getFilterAt(int index) {
      return myFilters.get(index);
    }

    public int getFilterIndex(com.intellij.java.debugger.ui.classFilter.ClassFilter filter) {
      return myFilters.indexOf(filter);
    }

    public void addRow(com.intellij.java.debugger.ui.classFilter.ClassFilter filter) {
      myFilters.add(filter);
      int row = myFilters.size() - 1;
      fireTableRowsInserted(row, row);
    }

    public int getRowCount() {
      return myFilters.size();
    }

    public int getColumnCount() {
      return 2;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      com.intellij.java.debugger.ui.classFilter.ClassFilter filter = myFilters.get(rowIndex);
      if (columnIndex == FILTER) {
        return filter;
      }
      if (columnIndex == CHECK_MARK) {
        return filter.isEnabled() ? Boolean.TRUE : Boolean.FALSE;
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      com.intellij.java.debugger.ui.classFilter.ClassFilter filter = myFilters.get(rowIndex);
      if (columnIndex == FILTER) {
        filter.setPattern(aValue != null ? aValue.toString() : "");
      }
      else if (columnIndex == CHECK_MARK) {
        filter.setEnabled(aValue == null || (Boolean)aValue);
      }
//      fireTableCellUpdated(rowIndex, columnIndex);
      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_MARK) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return isEnabled() && (columnIndex == CHECK_MARK);
    }

    public void removeRow(final int idx) {
      myFilters.remove(idx);
      fireTableDataChanged();
    }
  }

  private class FilterCellRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(
      JTable table,
      Object value,
      boolean isSelected,
      boolean hasFocus,
      int row,
      int column
    ) {
      Color color = UIUtil.getTableFocusCellBackground();
      UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, table.getSelectionBackground());
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (component instanceof JLabel) {
        ((JLabel)component).setBorder(noFocusBorder);
      }
      UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, color);
      com.intellij.java.debugger.ui.classFilter.ClassFilter filter =
        (com.intellij.java.debugger.ui.classFilter.ClassFilter)table.getValueAt(row, FilterTableModel.FILTER);
      component.setEnabled(ClassFilterEditor.this.isEnabled() && filter.isEnabled());
      return component;
    }
  }

  private class EnabledCellRenderer extends DefaultTableCellRenderer {
    private final TableCellRenderer myDelegate;

    public EnabledCellRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setEnabled(ClassFilterEditor.this.isEnabled());
      return component;
    }
  }

  @Nonnull
  protected com.intellij.java.debugger.ui.classFilter.ClassFilter createFilter(String pattern) {
    return new com.intellij.java.debugger.ui.classFilter.ClassFilter(pattern);
  }

  protected void addPatternFilter() {
    ClassFilterEditorAddDialog dialog = new ClassFilterEditorAddDialog(myProject, myPatternsHelpId);
    dialog.show();
    if (dialog.isOK()) {
      String pattern = dialog.getPattern();
      if (pattern != null) {
        com.intellij.java.debugger.ui.classFilter.ClassFilter filter = createFilter(pattern);
        myTableModel.addRow(filter);
        int row = myTableModel.getRowCount() - 1;
        myTable.getSelectionModel().setSelectionInterval(row, row);
        myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

        myTable.requestFocus();
      }
    }
  }

  @RequiredReadAction
  protected void addClassFilter() {
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createNoInnerClassesScopeChooser(
      UILocalize.classFilterEditorChooseClassTitle().get(),
      GlobalSearchScope.allScope(myProject),
      myChooserFilter,
      null
    );
    chooser.showDialog();
    PsiClass selectedClass = chooser.getSelected();
    if (selectedClass != null) {
      com.intellij.java.debugger.ui.classFilter.ClassFilter filter = createFilter(getJvmClassName(selectedClass));
      myTableModel.addRow(filter);
      int row = myTableModel.getRowCount() - 1;
      myTable.getSelectionModel().setSelectionInterval(row, row);
      myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

      myTable.requestFocus();
    }
  }

  @Nullable
  @RequiredReadAction
  private static String getJvmClassName(PsiClass aClass) {
    PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    if (parentClass != null) {
      final String parentName = getJvmClassName(parentClass);
      if (parentName == null) {
        return null;
      }
      return parentName + "$" + aClass.getName();
    }
    return aClass.getQualifiedName();
  }

  public void addPattern(String pattern) {
    com.intellij.java.debugger.ui.classFilter.ClassFilter filter = createFilter(pattern);
    myTableModel.addRow(filter);
  }
}
