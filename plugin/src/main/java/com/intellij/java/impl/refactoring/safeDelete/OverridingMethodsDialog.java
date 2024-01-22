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
package com.intellij.java.impl.refactoring.safeDelete;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.safeDelete.usageInfo.SafeDeleteOverridingMethodUsageInfo;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.application.HelpManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.util.ui.Table;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.project.Project;
import consulo.ui.ex.awt.BooleanTableCellRenderer;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.Splitter;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.usage.UsageInfo;
import consulo.usage.UsagePreviewPanel;
import consulo.usage.UsagePreviewPanelFactory;
import consulo.usage.UsageViewPresentation;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author dsl
 */
class OverridingMethodsDialog extends DialogWrapper {
  private final List<UsageInfo> myOverridingMethods;
  private final String[] myMethodText;
  private final boolean[] myChecked;

  private static final int CHECK_COLUMN = 0;
  private consulo.ide.impl.idea.util.ui.Table myTable;
  private final UsagePreviewPanel myUsagePreviewPanel;

  public OverridingMethodsDialog(Project project, List<UsageInfo> overridingMethods) {
    super(project, true);
    myOverridingMethods = overridingMethods;
    myChecked = new boolean[myOverridingMethods.size()];
    for (int i = 0; i < myChecked.length; i++) {
      myChecked[i] = true;
    }

    myMethodText = new String[myOverridingMethods.size()];
    for (int i = 0; i < myMethodText.length; i++) {
      myMethodText[i] = PsiFormatUtil.formatMethod(((SafeDeleteOverridingMethodUsageInfo) myOverridingMethods.get(i)).getOverridingMethod(),
          PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS
              | PsiFormatUtilBase.SHOW_TYPE, PsiFormatUtilBase.SHOW_TYPE);
    }
    myUsagePreviewPanel = UsagePreviewPanelFactory.getInstance().createPreviewPanel(project, new UsageViewPresentation());
    setTitle(RefactoringBundle.message("unused.overriding.methods.title"));
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.safeDelete.OverridingMethodsDialog";
  }

  public ArrayList<UsageInfo> getSelected() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    for (int i = 0; i < myChecked.length; i++) {
      if (myChecked[i]) {
        result.add(myOverridingMethods.get(i));
      }
    }
    return result;
  }

  @Override
  @Nonnull
  protected Action[] createActions() {
    return new Action[]{
        getOKAction(),
        getCancelAction()/*, getHelpAction()*/
    };
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.SAFE_DELETE_OVERRIDING);
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(new JLabel(RefactoringBundle.message("there.are.unused.methods.that.override.methods.you.delete")));
    panel.add(new JLabel(RefactoringBundle.message("choose.the.ones.you.want.to.be.deleted")));
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  @Override
  protected void dispose() {
    Disposer.dispose(myUsagePreviewPanel);
    super.dispose();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
    final MyTableModel tableModel = new MyTableModel();
    myTable = new Table(tableModel);
    myTable.setShowGrid(false);

    TableColumnModel columnModel = myTable.getColumnModel();
    //    columnModel.getColumn(DISPLAY_NAME_COLUMN).setCellRenderer(new MemberSelectionTable.MyTableRenderer());
    TableColumn checkboxColumn = columnModel.getColumn(CHECK_COLUMN);
    TableUtil.setupCheckboxColumn(checkboxColumn);
    checkboxColumn.setCellRenderer(new BooleanTableCellRenderer());

    // make SPACE check/uncheck selected rows
    @NonNls InputMap inputMap = myTable.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    @NonNls final ActionMap actionMap = myTable.getActionMap();
    actionMap.put("enable_disable", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) {
          return;
        }
        int[] rows = myTable.getSelectedRows();
        if (rows.length > 0) {
          boolean valueToBeSet = false;
          for (int row : rows) {
            if (!myChecked[row]) {
              valueToBeSet = true;
              break;
            }
          }
          for (int row : rows) {
            myChecked[row] = valueToBeSet;
          }

          tableModel.updateData();
        }
      }
    });



    /*Border titledBorder = IdeBorderFactory.createBoldTitledBorder("Select methods");
  Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    panel.setBorder(border);*/
    panel.setLayout(new BorderLayout());

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);

    panel.add(scrollPane, BorderLayout.CENTER);
    ListSelectionListener selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        int index = myTable.getSelectionModel().getLeadSelectionIndex();
        if (index != -1) {
          UsageInfo usageInfo = myOverridingMethods.get(index);
          myUsagePreviewPanel.updateLayout(Collections.singletonList(usageInfo));
        } else {
          myUsagePreviewPanel.updateLayout(null);
        }
      }
    };
    myTable.getSelectionModel().addListSelectionListener(selectionListener);

    final Splitter splitter = new Splitter(true, 0.3f);
    splitter.setFirstComponent(panel);
    splitter.setSecondComponent(myUsagePreviewPanel.createComponent());
    myUsagePreviewPanel.updateLayout(null);

    Disposer.register(myDisposable, new Disposable() {
      @Override
      public void dispose() {
        splitter.dispose();
      }
    });

    if (tableModel.getRowCount() != 0) {
      myTable.getSelectionModel().addSelectionInterval(0, 0);
    }
    return splitter;
  }

  class MyTableModel extends AbstractTableModel {
    @Override
    public int getRowCount() {
      return myChecked.length;
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case CHECK_COLUMN:
          return " ";
        default:
          return RefactoringBundle.message("method.column");
      }
    }

    @Override
    public Class getColumnClass(int columnIndex) {
      switch (columnIndex) {
        case CHECK_COLUMN:
          return Boolean.class;
        default:
          return String.class;
      }
    }


    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_COLUMN) {
        return Boolean.valueOf(myChecked[rowIndex]);
      } else {
        return myMethodText[rowIndex];
      }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_COLUMN) {
        myChecked[rowIndex] = ((Boolean) aValue).booleanValue();
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == CHECK_COLUMN;
    }

    void updateData() {
      fireTableDataChanged();
    }
  }
}
