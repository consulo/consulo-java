/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.migration;

import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.table.JBTable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.*;

public class EditMigrationDialog extends DialogWrapper {
  private JBTable myTable;
  private JTextField myNameField;
  private JTextArea myDescriptionTextArea;
  private final Project myProject;
  private final MigrationMap myMigrationMap;

  public EditMigrationDialog(Project project, MigrationMap migrationMap) {
    super(project, true);
    myProject = project;
    myMigrationMap = migrationMap;
    setHorizontalStretch(1.2f);
    setTitle(RefactoringLocalize.editMigrationMapTitle());
    init();
    validateOKButton();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private void validateOKButton() {
    boolean isEnabled = true;
    if (myNameField.getText().trim().length() == 0) {
      isEnabled = false;
    } else if (myMigrationMap.getEntryCount() == 0) {
      isEnabled = false;
    }
    setOKActionEnabled(isEnabled);
  }

  public String getName() {
    return myNameField.getText();
  }

  public String getDescription() {
    return myDescriptionTextArea.getText();
  }

  @Override
  protected JComponent createNorthPanel() {
    myNameField = new JTextField(myMigrationMap.getName());
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validateOKButton();
      }
    });

    myDescriptionTextArea = new JTextArea(myMigrationMap.getDescription(), 3, 40) {
      @Override
      public Dimension getMinimumSize() {
        return super.getPreferredSize();
      }
    };
    myDescriptionTextArea.setLineWrap(true);
    myDescriptionTextArea.setWrapStyleWord(true);
    myDescriptionTextArea.setFont(myNameField.getFont());
    myDescriptionTextArea.setBackground(myNameField.getBackground());
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myDescriptionTextArea);
    scrollPane.setBorder(myNameField.getBorder());

    return FormBuilder.createFormBuilder()
      .addLabeledComponent(new JLabel(RefactoringLocalize.migrationMapNamePrompt().get()), myNameField)
      .addLabeledComponent(new JLabel(RefactoringLocalize.migrationMapDescriptionLabel().get()), scrollPane)
      .addVerticalGap(UIUtil.LARGE_VGAP)
      .getPanel();
  }

  @Override
  protected JComponent createCenterPanel() {
    return ToolbarDecorator.createDecorator(createTable()).setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        addRow();
        validateOKButton();
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        removeRow();
        validateOKButton();
      }
    }).setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        edit();
      }
    }).setMoveUpAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        moveUp();
      }
    }).setMoveDownAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        moveDown();
      }
    }).createPanel();
  }

  private void edit() {
    EditMigrationEntryDialog dialog = new EditMigrationEntryDialog(myProject);
    int selected = myTable.getSelectedRow();
    if (selected < 0) {
      return;
    }
    MigrationMapEntry entry = myMigrationMap.getEntryAt(selected);
    dialog.setEntry(entry);
    if (!dialog.showAndGet()) {
      return;
    }
    dialog.updateEntry(entry);
    AbstractTableModel model = (AbstractTableModel) myTable.getModel();
    model.fireTableRowsUpdated(selected, selected);
  }

  private void addRow() {
    EditMigrationEntryDialog dialog = new EditMigrationEntryDialog(myProject);
    MigrationMapEntry entry = new MigrationMapEntry();
    dialog.setEntry(entry);
    if (!dialog.showAndGet()) {
      return;
    }
    dialog.updateEntry(entry);
    myMigrationMap.addEntry(entry);
    AbstractTableModel model = (AbstractTableModel) myTable.getModel();
    model.fireTableRowsInserted(myMigrationMap.getEntryCount() - 1, myMigrationMap.getEntryCount() - 1);
    myTable.setRowSelectionInterval(myMigrationMap.getEntryCount() - 1, myMigrationMap.getEntryCount() - 1);
  }

  private void removeRow() {
    int selected = myTable.getSelectedRow();
    if (selected < 0) {
      return;
    }
    myMigrationMap.removeEntryAt(selected);
    AbstractTableModel model = (AbstractTableModel) myTable.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if (selected >= myMigrationMap.getEntryCount()) {
      selected--;
    }
    if (selected >= 0) {
      myTable.setRowSelectionInterval(selected, selected);
    }
  }

  private void moveUp() {
    int selected = myTable.getSelectedRow();
    if (selected < 1) {
      return;
    }
    MigrationMapEntry entry = myMigrationMap.getEntryAt(selected);
    MigrationMapEntry previousEntry = myMigrationMap.getEntryAt(selected - 1);
    myMigrationMap.setEntryAt(previousEntry, selected);
    myMigrationMap.setEntryAt(entry, selected - 1);
    AbstractTableModel model = (AbstractTableModel) myTable.getModel();
    model.fireTableRowsUpdated(selected - 1, selected);
    myTable.setRowSelectionInterval(selected - 1, selected - 1);
  }

  private void moveDown() {
    int selected = myTable.getSelectedRow();
    if (selected >= myMigrationMap.getEntryCount() - 1) {
      return;
    }
    MigrationMapEntry entry = myMigrationMap.getEntryAt(selected);
    MigrationMapEntry nextEntry = myMigrationMap.getEntryAt(selected + 1);
    myMigrationMap.setEntryAt(nextEntry, selected);
    myMigrationMap.setEntryAt(entry, selected + 1);
    AbstractTableModel model = (AbstractTableModel) myTable.getModel();
    model.fireTableRowsUpdated(selected, selected + 1);
    myTable.setRowSelectionInterval(selected + 1, selected + 1);
  }

  private JBTable createTable() {
    final LocalizeValue[] names = {
      RefactoringLocalize.migrationTypeColumnHeader(),
      RefactoringLocalize.migrationOldNameColumnHeader(),
      RefactoringLocalize.migrationNewNameColumnHeader()
    };

    // Create a model of the data.
    TableModel dataModel = new AbstractTableModel() {
      @Override
      public int getColumnCount() {
        return 3;
      }

      @Override
      public int getRowCount() {
        return myMigrationMap.getEntryCount();
      }

      @Override
      public Object getValueAt(int row, int col) {
        MigrationMapEntry entry = myMigrationMap.getEntryAt(row);
        if (col == 0) {
          if (entry.getType() == MigrationMapEntry.PACKAGE && entry.isRecursive()) {
            return RefactoringLocalize.migrationPackageWithSubpackages().get();
          } else if (entry.getType() == MigrationMapEntry.PACKAGE && !entry.isRecursive()) {
            return RefactoringLocalize.migrationPackage().get();
          } else {
            return RefactoringLocalize.migrationClass().get();
          }
        }

        String suffix = (entry.getType() == MigrationMapEntry.PACKAGE ? ".*" : "");
        if (col == 1) {
          return entry.getOldName() + suffix;
        } else {
          return entry.getNewName() + suffix;
        }
      }

      @Override
      public String getColumnName(int column) {
        return names[column].get();
      }

      @Override
      public Class getColumnClass(int c) {
        return String.class;
      }

      @Override
      public boolean isCellEditable(int row, int col) {
        return false;
      }

      @Override
      public void setValueAt(Object aValue, int row, int column) {
      }
    };

    // Create the table
    myTable = new JBTable(dataModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myTable.setPreferredScrollableViewportSize(new Dimension(300, myTable.getRowHeight() * 10));

    return myTable;
  }
}