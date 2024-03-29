/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.compiler.impl.options;

import com.intellij.java.compiler.impl.javaCompiler.annotationProcessing.ProcessorConfigProfile;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.IdeaFileChooser;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.List;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 5/28/12
 */
public class ProcessorProfilePanel extends JPanel {
  private final Project myProject;

  private JRadioButton myRbClasspath;
  private JRadioButton myRbProcessorsPath;
  private TextFieldWithBrowseButton myProcessorPathField;
  private JTextField myGeneratedProductionDirField;
  private JTextField myGeneratedTestsDirField;
  private JRadioButton myRbRelativeToOutputRoot;
  private JRadioButton myRbRelativeToContentRoot;
  private ProcessorTableModel myProcessorsModel;
  private JCheckBox myCbEnableProcessing;
  private JBTable myProcessorTable;
  private JBTable myOptionsTable;
  private JPanel myProcessorPanel;
  private JPanel myOptionsPanel;
  private OptionsTableModel myOptionsModel;
  private JLabel myWarninglabel;
  private JLabel myStoreGenSourcesLabel;
  private JLabel myProductionLabel;
  private JLabel myTestLabel;
  private JPanel myProcessorTablePanel;
  private JPanel myOptionsTablePanel;


  public ProcessorProfilePanel(Project project) {
    super(new GridBagLayout());
    myProject = project;

    myCbEnableProcessing = new JCheckBox("Enable annotation processing");

    {
      myRbClasspath = new JRadioButton("Obtain processors from project classpath");
      myRbProcessorsPath = new JRadioButton("Processor path:");
      ButtonGroup group = new ButtonGroup();
      group.add(myRbClasspath);
      group.add(myRbProcessorsPath);
    }

    {
      myRbRelativeToContentRoot = new JRadioButton("Module content root");
      myRbRelativeToOutputRoot = new JRadioButton("Module output directory");
      final ButtonGroup group = new ButtonGroup();
      group.add(myRbRelativeToContentRoot);
      group.add(myRbRelativeToOutputRoot);
    }

    myProcessorPathField = new TextFieldWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor();
        final VirtualFile[] files = IdeaFileChooser.chooseFiles(descriptor, myProcessorPathField, myProject, null);
        if (files.length > 0) {
          final StringBuilder builder = new StringBuilder();
          for (VirtualFile file : files) {
            if (builder.length() > 0) {
              builder.append(File.pathSeparator);
            }
            builder.append(FileUtil.toSystemDependentName(file.getPath()));
          }
          myProcessorPathField.setText(builder.toString());
        }
      }
    });

    myProcessorTablePanel = new JPanel(new BorderLayout());
    myProcessorsModel = new ProcessorTableModel();
    myProcessorTablePanel.setBorder(IdeBorderFactory.createTitledBorder("Annotation Processors", false));
    myProcessorTable = new JBTable(myProcessorsModel);
    myProcessorTable.getEmptyText().setText("Compiler will run all automatically discovered processors");
    myProcessorPanel = createTablePanel(myProcessorTable);
    myProcessorTablePanel.add(myProcessorPanel, BorderLayout.CENTER);

    myOptionsTablePanel = new JPanel(new BorderLayout());
    myOptionsModel = new OptionsTableModel();
    myOptionsTablePanel.setBorder(IdeBorderFactory.createTitledBorder("Annotation Processor options", false));
    myOptionsTable = new JBTable(myOptionsModel);
    myOptionsTable.getEmptyText().setText("No processor-specific options configured");
    myOptionsPanel = createTablePanel(myOptionsTable);
    myOptionsTablePanel.add(myOptionsPanel, BorderLayout.CENTER);

    myGeneratedProductionDirField = new JTextField();
    myGeneratedTestsDirField = new JTextField();

    myWarninglabel = new JLabel("<html>WARNING!<br>" +
                                              /*"All source files located in the generated sources output directory WILL BE EXCLUDED from annotation processing. " +*/
                                              "If option 'Clear output directory on rebuild' is enabled, " +
                                              "the entire contents of directories where generated sources are stored WILL BE CLEARED on rebuild.</html>");
    myWarninglabel.setFont(myWarninglabel.getFont().deriveFont(Font.BOLD));

    add(myCbEnableProcessing,
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    add(myRbClasspath,
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
    add(myRbProcessorsPath,
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    add(myProcessorPathField,
        new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 0, 0), 0, 0));

    myStoreGenSourcesLabel = new JLabel("Store generated sources relative to: ");
    add(myStoreGenSourcesLabel,
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(15, 5, 0, 0), 0, 0));
    add(myRbRelativeToOutputRoot,
        new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(15, 5, 0, 0), 0, 0));
    add(myRbRelativeToContentRoot,
        new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(15, 5, 0, 0), 0, 0));

    myProductionLabel = new JLabel("Production sources directory:");
    add(myProductionLabel,
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));
    add(myGeneratedProductionDirField,
        new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));

    myTestLabel = new JLabel("Test sources directory:");
    add(myTestLabel,
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));
    add(myGeneratedTestsDirField,
        new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));

    add(myProcessorTablePanel,
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(10, 0, 0, 0), 0, 0));
    add(myOptionsTablePanel,
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(10, 0, 0, 0), 0, 0));
    add(myWarninglabel,
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));

    myRbClasspath.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledState();
      }
    });

    myProcessorTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          updateEnabledState();
        }
      }
    });

    myCbEnableProcessing.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledState();
      }
    });

    updateEnabledState();

  }

  public void setProfile(ProcessorConfigProfile config) {
    myCbEnableProcessing.setSelected(config.isEnabled());

    (config.isObtainProcessorsFromClasspath()? myRbClasspath : myRbProcessorsPath).setSelected(true);
    myProcessorPathField.setText(FileUtil.toSystemDependentName(config.getProcessorPath()));

    final String productionDirName = config.getGeneratedSourcesDirectoryName(false);
    myGeneratedProductionDirField.setText(productionDirName != null? productionDirName.trim() : "");
    final String testsDirName = config.getGeneratedSourcesDirectoryName(true);
    myGeneratedTestsDirField.setText(testsDirName != null? testsDirName.trim() : "");
    if (config.isOutputRelativeToContentRoot()) {
      myRbRelativeToContentRoot.setSelected(true);
    }
    else {
      myRbRelativeToOutputRoot.setSelected(true);
    }
    myProcessorsModel.setProcessors(config.getProcessors());
    myOptionsModel.setOptions(config.getProcessorOptions());

    updateEnabledState();
  }

  public void saveTo(ProcessorConfigProfile profile) {
    profile.setEnabled(myCbEnableProcessing.isSelected());
    profile.setObtainProcessorsFromClasspath(myRbClasspath.isSelected());
    profile.setProcessorPath(myProcessorPathField.getText().trim());

    final String productionDir = myGeneratedProductionDirField.getText().trim();
    profile.setGeneratedSourcesDirectoryName(StringUtil.isEmpty(productionDir)? null : productionDir, false);
    final String testsDir = myGeneratedTestsDirField.getText().trim();
    profile.setGeneratedSourcesDirectoryName(StringUtil.isEmpty(testsDir)? null : testsDir, true);

    profile.setOutputRelativeToContentRoot(myRbRelativeToContentRoot.isSelected());

    profile.clearProcessors();
    for (String processor : myProcessorsModel.getProcessors()) {
      profile.addProcessor(processor);
    }
    profile.clearProcessorOptions();
    for (Map.Entry<String, String> entry : myOptionsModel.getOptions().entrySet()) {
      profile.setOption(entry.getKey(), entry.getValue());
    }
  }

  private static JPanel createTablePanel(final JBTable table) {
    return ToolbarDecorator.createDecorator(table)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          final TableCellEditor cellEditor = table.getCellEditor();
          if (cellEditor != null) {
            cellEditor.stopCellEditing();
          }
          final TableModel model = table.getModel();
          ((EditableModel)model).addRow();
          TableUtil.editCellAt(table, model.getRowCount() - 1, 0);
        }
      })
      .createPanel();
  }

  private void updateEnabledState() {
   final boolean enabled = myCbEnableProcessing.isSelected();
    final boolean useProcessorpath = !myRbClasspath.isSelected();
    myRbClasspath.setEnabled(enabled);
    myRbProcessorsPath.setEnabled(enabled);
    myProcessorPathField.setEnabled(enabled && useProcessorpath);
    updateTable(myProcessorPanel, myProcessorTable, enabled);
    updateTable(myOptionsPanel, myOptionsTable, enabled);
    myGeneratedProductionDirField.setEnabled(enabled);
    myGeneratedTestsDirField.setEnabled(enabled);
    myRbRelativeToOutputRoot.setEnabled(enabled);
    myRbRelativeToContentRoot.setEnabled(enabled);
    myWarninglabel.setEnabled(enabled);
    myStoreGenSourcesLabel.setEnabled(enabled);
    myProductionLabel.setEnabled(enabled);
    myTestLabel.setEnabled(enabled);
    myProcessorTablePanel.setEnabled(enabled);
    myOptionsTablePanel.setEnabled(enabled);
  }

  private static void updateTable(final JPanel tablePanel, final JBTable table, boolean enabled) {
    final AnActionButton addButton = ToolbarDecorator.findAddButton(tablePanel);
    if (addButton != null) {
      addButton.setEnabled(enabled);
    }
    final AnActionButton removeButton = ToolbarDecorator.findRemoveButton(tablePanel);
    if (removeButton != null) {
      removeButton.setEnabled(enabled && table.getSelectedRow() >= 0);
    }
    table.setEnabled(enabled);
    final JTableHeader header = table.getTableHeader();
    if (header != null) {
      header.repaint();
    }
  }

  private static class OptionsTableModel extends AbstractTableModel implements EditableModel {
    private final java.util.List<KeyValuePair> myRows = new ArrayList<KeyValuePair>();

    public String getColumnName(int column) {
      switch (column) {
        case 0: return "Option Name";
        case 1: return "Value";
      }
      return super.getColumnName(column);
    }

    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    public int getRowCount() {
      return myRows.size();
    }

    public int getColumnCount() {
      return 2;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0 || columnIndex == 1;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case 0: return myRows.get(rowIndex).key;
        case 1: return myRows.get(rowIndex).value;
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (aValue != null) {
        switch (columnIndex) {
          case 0:
            myRows.get(rowIndex).key = (String)aValue;
            break;
          case 1:
            myRows.get(rowIndex).value = (String)aValue;
            break;
        }
      }
    }

    public void removeRow(int idx) {
      myRows.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return false;
    }

    public void addRow() {
      myRows.add(new KeyValuePair());
      final int index = myRows.size() - 1;
      fireTableRowsInserted(index, index);
    }

    public void setOptions(Map<String, String> options) {
      clear();
      if (!options.isEmpty()) {
        for (Map.Entry<String, String> entry : options.entrySet()) {
          myRows.add(new KeyValuePair(entry.getKey(), entry.getValue()));
        }
        Collections.sort(myRows, new Comparator<KeyValuePair>() {
          @Override
          public int compare(KeyValuePair o1, KeyValuePair o2) {
            return o1.key.compareToIgnoreCase(o2.key);
          }
        });
        fireTableRowsInserted(0, options.size()-1);
      }
    }

    public void clear() {
      final int count = myRows.size();
      if (count > 0) {
        myRows.clear();
        fireTableRowsDeleted(0, count-1);
      }
    }

    public Map<String, String> getOptions() {
      final Map<String, String> map = new HashMap<String, String>();
      for (KeyValuePair pair : myRows) {
        map.put(pair.key.trim(), pair.value.trim());
      }
      map.remove("");
      return map;
    }

    private static final class KeyValuePair {
      String key;
      String value;

      KeyValuePair() {
        this("", "");
      }

      KeyValuePair(String key, String value) {
        this.key = key;
        this.value = value;
      }
    }
  }

  private static class ProcessorTableModel extends AbstractTableModel implements EditableModel {
    private final List<String> myRows = new ArrayList<String>();

    public String getColumnName(int column) {
      switch (column) {
        case 0: return "Processor FQ Name";
      }
      return super.getColumnName(column);
    }

    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    public int getRowCount() {
      return myRows.size();
    }

    public int getColumnCount() {
      return 1;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case 0: return myRows.get(rowIndex);
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (aValue != null) {
        switch (columnIndex) {
          case 0:
            myRows.set(rowIndex, (String)aValue);
            break;
        }
      }
    }

    public void removeRow(int idx) {
      myRows.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return false;
    }

    public void addRow() {
      myRows.add("");
      final int index = myRows.size() - 1;
      fireTableRowsInserted(index, index);
    }

    public void setProcessors(Collection<String> processors) {
      clear();
      if (!processors.isEmpty()) {
        for (String processor : processors) {
          myRows.add(processor);
        }
        Collections.sort(myRows, new Comparator<String>() {
          public int compare(String o1, String o2) {
            return o1.compareToIgnoreCase(o2);
          }
        });
        fireTableRowsInserted(0, processors.size()-1);
      }
    }

    public void clear() {
      final int count = myRows.size();
      if (count > 0) {
        myRows.clear();
        fireTableRowsDeleted(0, count-1);
      }
    }

    public Collection<String> getProcessors() {
      final Set<String> set = new HashSet<String>();
      for (String row : myRows) {
        if (row != null) {
          set.add(row.trim());
        }
      }
      set.remove("");
      return set;
    }
  }

}
