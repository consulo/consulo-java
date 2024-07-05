/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.changeClassSignature;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.ui.JavaCodeFragmentTableCellEditor;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.*;
import consulo.ide.impl.idea.refactoring.ui.CodeFragmentTableCellRenderer;
import consulo.ide.impl.idea.ui.TableColumnAnimator;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.language.editor.refactoring.ui.StringTableCellEditor;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ColoredTableCellRenderer;
import consulo.ui.ex.awt.EditableModel;
import consulo.ui.ex.awt.SeparatorFactory;
import consulo.ui.ex.awt.ToolbarDecorator;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author dsl
 * @author Konstantin Bulenkov
 */
public class ChangeClassSignatureDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance(ChangeClassSignatureDialog.class);
  private static final int NAME_COLUMN = 0;
  private static final int VALUE_COLUMN = 1;

  private final List<TypeParameterInfo> myTypeParameterInfos;
  private final List<PsiTypeCodeFragment> myTypeCodeFragments;
  private final PsiClass myClass;
  private final PsiTypeParameter[] myOriginalParameters;
  private final Project myProject;
  private final MyTableModel myTableModel;
  private JBTable myTable;
  static final String REFACTORING_NAME = RefactoringBundle.message("changeClassSignature.refactoring.name");
  private boolean myHideDefaultValueColumn;

  public ChangeClassSignatureDialog(@Nonnull PsiClass aClass, boolean hideDefaultValueColumn) {
    this(
      aClass,
      initTypeParameterInfos(aClass.getTypeParameters().length),
      initTypeCodeFragment(aClass.getTypeParameters().length),
      hideDefaultValueColumn
    );
  }

  @Nonnull
  private static List<TypeParameterInfo> initTypeParameterInfos(int length) {
    final List<TypeParameterInfo> result = new ArrayList<TypeParameterInfo>();
    for (int i = 0; i < length; i++) {
      result.add(new TypeParameterInfo(i));
    }
    return result;
  }

  @Nonnull
  private static List<PsiTypeCodeFragment> initTypeCodeFragment(int length) {
    final List<PsiTypeCodeFragment> result = new ArrayList<PsiTypeCodeFragment>();
    for (int i = 0; i < length; i++) {
      result.add(null);
    }
    return result;
  }

  public ChangeClassSignatureDialog(@Nonnull PsiClass aClass,
                                    @Nonnull Map<TypeParameterInfo, PsiTypeCodeFragment> parameters,
                                    boolean hideDefaultValueColumn) {
    this(aClass, parameters.keySet(), parameters.values(), hideDefaultValueColumn);
  }

  public ChangeClassSignatureDialog(@Nonnull PsiClass aClass,
                                    @Nonnull Collection<TypeParameterInfo> typeParameterInfos,
                                    @Nonnull Collection<PsiTypeCodeFragment> typeCodeFragments,
                                    boolean hideDefaultValueColumn) {
    super(aClass.getProject(), true);
    myHideDefaultValueColumn = hideDefaultValueColumn;
    setTitle(REFACTORING_NAME);
    myClass = aClass;
    myProject = myClass.getProject();
    myOriginalParameters = myClass.getTypeParameters();
    myTypeParameterInfos = new ArrayList<TypeParameterInfo>(typeParameterInfos);
    myTypeCodeFragments = new ArrayList<PsiTypeCodeFragment>(typeCodeFragments);
    myTableModel = new MyTableModel();
    init();
  }

  private PsiTypeCodeFragment createValueCodeFragment() {
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
    return factory.createTypeCodeFragment("", myClass.getLBrace(), true);
  }

  protected JComponent createNorthPanel() {
    return new JLabel(RefactoringLocalize.changeclasssignatureClassLabelText(DescriptiveNameUtil.getDescriptiveName(myClass)).get());
  }

  @Override
  protected String getHelpId() {
    return HelpID.CHANGE_CLASS_SIGNATURE;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  protected JComponent createCenterPanel() {
    myTable = new JBTable(myTableModel);
    myTable.setStriped(true);
    TableColumn nameColumn = myTable.getColumnModel().getColumn(NAME_COLUMN);
    TableColumn valueColumn = myTable.getColumnModel().getColumn(VALUE_COLUMN);
    Project project = myClass.getProject();
    nameColumn.setCellRenderer(new MyCellRenderer());
    nameColumn.setCellEditor(new StringTableCellEditor(project));
    valueColumn.setCellRenderer(new CodeFragmentTableCellRenderer(project, JavaFileType.INSTANCE));
    valueColumn.setCellEditor(new JavaCodeFragmentTableCellEditor(project));

    myTable.setPreferredScrollableViewportSize(new Dimension(210, myTable.getRowHeight() * 4));
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getSelectionModel().setSelectionInterval(0, 0);
    myTable.setSurrendersFocusOnKeystroke(true);
    myTable.setCellSelectionEnabled(true);
    myTable.setFocusCycleRoot(true);

    if (myHideDefaultValueColumn) {
      final TableColumn defaultValue = myTable.getColumnModel().getColumn(VALUE_COLUMN);
      myTable.removeColumn(defaultValue);
      myTable.getModel().addTableModelListener(new TableModelListener() {
        @Override
        public void tableChanged(TableModelEvent e) {
          if (e.getType() == TableModelEvent.INSERT) {
            myTable.getModel().removeTableModelListener(this);
            final TableColumnAnimator animator = new TableColumnAnimator(myTable);
            animator.setStep(20);
            animator.addColumn(defaultValue, myTable.getWidth() / 2);
            animator.startAndDoWhenDone(new Runnable() {
              @Override
              public void run() {
                myTable.editCellAt(myTable.getRowCount() - 1, 0);
              }
            });
            animator.start();
          }
        }
      });
    }

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(SeparatorFactory.createSeparator(RefactoringLocalize.changeclasssignatureParametersPanelBorderTitle().get(), myTable), BorderLayout.NORTH);
    panel.add(ToolbarDecorator.createDecorator(myTable).createPanel(), BorderLayout.CENTER);
    return panel;
  }

  @RequiredUIAccess
  protected void doAction() {
    TableUtil.stopEditing(myTable);
    String message = validateAndCommitData();
    if (message != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringLocalize.errorIncorrectData().get(), message, HelpID.CHANGE_SIGNATURE, myClass.getProject());
      return;
    }
    ChangeClassSignatureProcessor processor =
      new ChangeClassSignatureProcessor(myClass.getProject(), myClass,
                                        myTypeParameterInfos.toArray(new TypeParameterInfo[myTypeParameterInfos.size()]));
    invokeRefactoring(processor);
  }

  private String validateAndCommitData() {
    final PsiTypeParameter[] parameters = myClass.getTypeParameters();
    final Map<String, TypeParameterInfo> infos = new HashMap<String, TypeParameterInfo>();
    for (final TypeParameterInfo info : myTypeParameterInfos) {
      if (!info.isForExistingParameter() &&
          !PsiNameHelper.getInstance(myClass.getProject()).isIdentifier(info.getNewName())) {
        return RefactoringLocalize.errorWrongNameInput(info.getNewName()).get();
      }
      final String newName = info.isForExistingParameter() ? parameters[info.getOldParameterIndex()].getName() : info.getNewName();
      TypeParameterInfo existing = infos.get(newName);
      if (existing != null) {
        return myClass.getName() + " already contains type parameter " + newName;
      }
      infos.put(newName, info);
    }
    LOG.assertTrue(myTypeCodeFragments.size() == myTypeParameterInfos.size());
    for (int i = 0; i < myTypeCodeFragments.size(); i++) {
      final PsiTypeCodeFragment codeFragment = myTypeCodeFragments.get(i);
      TypeParameterInfo info = myTypeParameterInfos.get(i);
      if (info.getOldParameterIndex() >= 0) continue;
      PsiType type;
      try {
        type = codeFragment.getType();
        if (type instanceof PsiPrimitiveType) {
          return "Type parameter can't be primitive";
        }
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return RefactoringLocalize.changeclasssignatureBadDefaultValue(codeFragment.getText(), info.getNewName()).get();
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        return RefactoringLocalize.changesignatureNoTypeForParameter(info.getNewName()).get();
      }
      info.setDefaultValue(type);
    }
    return null;
  }

  private class MyTableModel extends AbstractTableModel implements EditableModel {
    public int getColumnCount() {
      return 2;
    }

    public int getRowCount() {
      return myTypeParameterInfos.size();
    }

    @Nullable
    public Class getColumnClass(int columnIndex) {
      return columnIndex == NAME_COLUMN ? String.class : null;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case NAME_COLUMN:
          TypeParameterInfo info = myTypeParameterInfos.get(rowIndex);
          if (info.isForExistingParameter()) {
            return myOriginalParameters[info.getOldParameterIndex()].getName();
          }
          else {
            return info.getNewName();
          }
        case VALUE_COLUMN:
          return myTypeCodeFragments.get(rowIndex);
      }
      LOG.assertTrue(false);
      return null;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return !myTypeParameterInfos.get(rowIndex).isForExistingParameter();
    }

    public String getColumnName(int column) {
      switch (column) {
        case NAME_COLUMN:
          return RefactoringLocalize.columnNameName().get();
        case VALUE_COLUMN:
          return RefactoringLocalize.changesignatureDefaultValueColumn().get();
        default:
          LOG.assertTrue(false);
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case NAME_COLUMN:
          myTypeParameterInfos.get(rowIndex).setNewName((String)aValue);
          break;
        case VALUE_COLUMN:
          break;
        default:
          LOG.assertTrue(false);
      }
    }

    public void addRow() {
      TableUtil.stopEditing(myTable);
      myTypeParameterInfos.add(new TypeParameterInfo("", null));
      myTypeCodeFragments.add(createValueCodeFragment());
      final int row = myTypeCodeFragments.size() - 1;
      fireTableRowsInserted(row, row);
    }

    public void removeRow(int index) {
      myTypeParameterInfos.remove(index);
      myTypeCodeFragments.remove(index);
      fireTableDataChanged();
    }

    public void exchangeRows(int index1, int index2) {
      ContainerUtil.swapElements(myTypeParameterInfos, index1, index2);
      ContainerUtil.swapElements(myTypeCodeFragments, index1, index2);
      fireTableDataChanged();
      //fireTableRowsUpdated(Math.min(index1, index2), Math.max(index1, index2));
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return true;
    }
  }

  private class MyCellRenderer extends ColoredTableCellRenderer {

    public void customizeCellRenderer(JTable table, Object value,
                                      boolean isSelected, boolean hasFocus, int row, int col) {
      if (value == null) return;
      setPaintFocusBorder(false);
      acquireState(table, isSelected, false, row, col);
      getCellState().updateRenderer(this);
      append((String)value);
    }
  }
}
