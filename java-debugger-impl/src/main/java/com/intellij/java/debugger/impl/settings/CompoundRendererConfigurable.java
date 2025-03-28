/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.settings;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.DebuggerUtilsImpl;
import com.intellij.java.debugger.impl.JavaDebuggerEditorsProvider;
import com.intellij.java.debugger.impl.engine.JVMNameUtil;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.debugger.impl.ui.JavaDebuggerSupport;
import com.intellij.java.debugger.impl.ui.tree.render.*;
import com.intellij.java.language.impl.psi.impl.source.PsiTypeCodeFragmentImpl;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import consulo.application.ApplicationManager;
import consulo.disposer.Disposable;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.ui.XDebuggerExpressionEditor;
import consulo.execution.debug.ui.XDebuggerExpressionEditorFactory;
import consulo.language.editor.ui.awt.ReferenceEditorWithBrowseButton;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

class CompoundRendererConfigurable extends JPanel {
    private CompoundTypeRenderer myRenderer;
    private CompoundTypeRenderer myOriginalRenderer;
    private Project myProject;
    private final ClassNameEditorWithBrowseButton myClassNameField;
    private final JRadioButton myRbDefaultLabel;
    private final JRadioButton myRbExpressionLabel;
    private final JBCheckBox myShowTypeCheckBox;
    private final JBCheckBox myOnDemandCheckBox;
    private final JRadioButton myRbDefaultChildrenRenderer;
    private final JRadioButton myRbExpressionChildrenRenderer;
    private final JRadioButton myRbListChildrenRenderer;
    private final XDebuggerExpressionEditor myLabelEditor;
    private final XDebuggerExpressionEditor myChildrenEditor;
    private final XDebuggerExpressionEditor myChildrenExpandedEditor;
    private XDebuggerExpressionEditor myListChildrenEditor;
    private final JLabel myExpandedLabel;
    private JBTable myTable;
    private final JBCheckBox myAppendDefaultChildren;
    @NonNls
    private static final String EMPTY_PANEL_ID = "EMPTY";
    @NonNls
    private static final String DATA_PANEL_ID = "DATA";
    private static final int NAME_TABLE_COLUMN = 0;
    private static final int EXPRESSION_TABLE_COLUMN = 1;
    private static final int ONDEMAND_TABLE_COLUMN = 2;

    public CompoundRendererConfigurable(@Nonnull Disposable parentDisposable) {
        super(new CardLayout());

        if (myProject == null) {
            myProject = JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables();
        }

        myRbDefaultLabel = new JRadioButton(DebuggerBundle.message("label.compound.renderer.configurable.use.default.renderer"));
        myRbExpressionLabel = new JRadioButton(DebuggerBundle.message("label.compound.renderer.configurable.use.expression"));
        final ButtonGroup labelButtonsGroup = new ButtonGroup();
        labelButtonsGroup.add(myRbDefaultLabel);
        labelButtonsGroup.add(myRbExpressionLabel);

        myShowTypeCheckBox = new JBCheckBox(DebuggerBundle.message("label.compound.renderer.configurable.show.type"));
        myOnDemandCheckBox = new JBCheckBox(DebuggerBundle.message("label.compound.renderer.configurable.ondemand"));

        myRbDefaultChildrenRenderer = new JRadioButton(DebuggerBundle.message("label.compound.renderer.configurable.use.default.renderer"));
        myRbExpressionChildrenRenderer = new JRadioButton(DebuggerBundle.message("label.compound.renderer.configurable.use.expression"));
        myRbListChildrenRenderer = new JRadioButton(DebuggerBundle.message("label.compound.renderer.configurable.use.expression.list"));
        final ButtonGroup childrenButtonGroup = new ButtonGroup();
        childrenButtonGroup.add(myRbDefaultChildrenRenderer);
        childrenButtonGroup.add(myRbExpressionChildrenRenderer);
        childrenButtonGroup.add(myRbListChildrenRenderer);

        JavaDebuggerEditorsProvider editorsProvider = new JavaDebuggerEditorsProvider();

        XDebuggerExpressionEditorFactory factory = myProject.getInstance(XDebuggerExpressionEditorFactory.class);
        myLabelEditor = factory.create(editorsProvider, "ClassLabelExpression", null, XExpression.EMPTY_EXPRESSION, false, false, true);
        myChildrenEditor = factory.create(editorsProvider, "ClassChildrenExpression", null, XExpression.EMPTY_EXPRESSION, false, false, true);
        myChildrenExpandedEditor = factory.create(editorsProvider, "ClassChildrenExpression", null, XExpression.EMPTY_EXPRESSION, false, false, true);
        JComponent myChildrenListEditor = createChildrenListEditor(editorsProvider);

        final ItemListener updateListener = new ItemListener() {
            @Override
            public void itemStateChanged(@Nonnull ItemEvent e) {
                updateEnabledState();
            }
        };
        myRbExpressionLabel.addItemListener(updateListener);
        myRbListChildrenRenderer.addItemListener(updateListener);
        myRbExpressionChildrenRenderer.addItemListener(updateListener);

        myClassNameField = new ClassNameEditorWithBrowseButton(new ActionListener() {
            @Override
            public void actionPerformed(@Nonnull ActionEvent e) {
                PsiClass psiClass = DebuggerUtils.getInstance().chooseClassDialog(DebuggerBundle.message("title.compound.renderer.configurable.choose.renderer.reference.type"), myProject);
                if (psiClass != null) {
                    String qName = JVMNameUtil.getNonAnonymousClassName(psiClass);
                    myClassNameField.setText(qName);
                    updateContext(qName);
                }
            }
        }, myProject);
        myClassNameField.getEditorTextField().addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(@Nonnull FocusEvent e) {
                updateContext(myClassNameField.getText());
            }
        });

        myAppendDefaultChildren = new JBCheckBox(DebuggerBundle.message("label.compound.renderer.configurable.append.default.children"));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(new JLabel(DebuggerBundle.message("label.compound.renderer.configurable.apply.to")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints
            .NORTHWEST, GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0));
        panel.add(myClassNameField, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.insetsTop(4), 0, 0));

        panel.add(new JLabel(DebuggerBundle.message("label.compound.renderer.configurable.when.rendering")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints
            .NORTHWEST, GridBagConstraints.NONE, JBUI.insetsTop(20), 0, 0));
        panel.add(myShowTypeCheckBox, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insetsLeft(7), 0, 0));
        panel.add(myRbDefaultLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insetsLeft(10), 0, 0));
        panel.add(myRbExpressionLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insetsLeft(10), 0, 0));
        panel.add(myLabelEditor.getComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.insetsLeft
            (30), 0, 0));
        panel.add(myOnDemandCheckBox, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.insetsLeft(30), 0, 0));

        panel.add(new JLabel(DebuggerBundle.message("label.compound.renderer.configurable.when.expanding")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints
            .NORTHWEST, GridBagConstraints.NONE, JBUI.insetsTop(20), 0, 0));
        panel.add(myRbDefaultChildrenRenderer, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insetsLeft(10), 0,
            0));
        panel.add(myRbExpressionChildrenRenderer, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insetsLeft(10),
            0, 0));
        panel.add(myChildrenEditor.getComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.insetsLeft
            (30), 0, 0));
        myExpandedLabel = new JLabel(DebuggerBundle.message("label.compound.renderer.configurable.test.can.expand"));
        panel.add(myExpandedLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(4, 30, 0, 0), 0, 0));
        panel.add(myChildrenExpandedEditor.getComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI
            .insetsLeft(30), 0, 0));
        panel.add(myRbListChildrenRenderer, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.insetsLeft(10),
            0, 0));
        panel.add(myChildrenListEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, JBUI.insets(4, 30, 0, 0), 0, 0));
        panel.add(myAppendDefaultChildren, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insetsLeft(25), 0, 0));
        add(new JPanel(), EMPTY_PANEL_ID);
        add(panel, DATA_PANEL_ID);
    }

    public void setRenderer(NodeRenderer renderer) {
        if (renderer instanceof CompoundTypeRenderer) {
            myRenderer = (CompoundTypeRenderer) renderer;
            myOriginalRenderer = (CompoundTypeRenderer) renderer.clone();
        }
        else {
            myRenderer = myOriginalRenderer = null;
        }
        reset();
    }

    public CompoundTypeRenderer getRenderer() {
        return myRenderer;
    }

    private void updateContext(final String qName) {
        ApplicationManager.getApplication().runReadAction(() ->
        {
            Project project = myProject;
            if (project != null) {
                Pair<PsiElement, PsiType> pair = DebuggerUtilsImpl.getPsiClassAndType(qName, project);
                PsiElement context = pair.first;
                if (context != null) {
                    myLabelEditor.setContext(context);
                    myChildrenEditor.setContext(context);
                    myChildrenExpandedEditor.setContext(context);
                    myListChildrenEditor.setContext(context);
                }
            }
        });
    }

    private void updateEnabledState() {
        boolean isLabelRenderer = myRbExpressionLabel.isSelected();
        myLabelEditor.setEnabled(isLabelRenderer);
        myOnDemandCheckBox.setEnabled(isLabelRenderer);

        final boolean isChildrenExpression = myRbExpressionChildrenRenderer.isSelected();
        myChildrenExpandedEditor.setEnabled(isChildrenExpression);
        myExpandedLabel.setEnabled(isChildrenExpression);
        myChildrenEditor.setEnabled(isChildrenExpression);

        boolean isListChildren = myRbListChildrenRenderer.isSelected();
        myTable.setEnabled(isListChildren);
        myAppendDefaultChildren.setEnabled(isListChildren);
    }

    private JComponent createChildrenListEditor(JavaDebuggerEditorsProvider editorsProvider) {
        final MyTableModel tableModel = new MyTableModel();
        myTable = new JBTable(tableModel);

        XDebuggerExpressionEditorFactory factory = myProject.getInstance(XDebuggerExpressionEditorFactory.class);
        myListChildrenEditor = factory.create(editorsProvider, "NamedChildrenConfigurable", null, XExpression.EMPTY_EXPRESSION, false, false, false);
        JComponent editorComponent = myListChildrenEditor.getComponent();

        AbstractTableCellEditor editor = new AbstractTableCellEditor() {
            @Override
            public Object getCellEditorValue() {
                return TextWithImportsImpl.fromXExpression(myListChildrenEditor.getExpression());
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                myListChildrenEditor.setExpression(TextWithImportsImpl.toXExpression((TextWithImports) value));
                return editorComponent;
            }
        };
        editorComponent.registerKeyboardAction(e -> editor.stopCellEditing(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        editorComponent.registerKeyboardAction(e -> editor.cancelCellEditing(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        TableColumn exprColumn = myTable.getColumnModel().getColumn(EXPRESSION_TABLE_COLUMN);
        exprColumn.setCellEditor(editor);
        exprColumn.setCellRenderer(new DefaultTableCellRenderer() {
            @Nonnull
            @Override
            public Component getTableCellRendererComponent(@Nonnull JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                final TextWithImports textWithImports = (TextWithImports) value;
                final String text = (textWithImports != null) ? textWithImports.getText() : "";
                return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);
            }
        });

        return ToolbarDecorator.createDecorator(myTable).setAddAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
                tableModel.addRow("", DebuggerUtils.getInstance().createExpressionWithImports(""));
            }
        }).setRemoveAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
                int selectedRow = myTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < myTable.getRowCount()) {
                    getTableModel().removeRow(selectedRow);
                }
            }
        }).setMoveUpAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
                TableUtil.moveSelectedItemsUp(myTable);
            }
        }).setMoveDownAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
                TableUtil.moveSelectedItemsDown(myTable);
            }
        }).createPanel();
    }

    public boolean isModified() {
        if (myRenderer == null) {
            return false;
        }
        final CompoundTypeRenderer cloned = (CompoundTypeRenderer) myRenderer.clone();
        flushDataTo(cloned);
        return !DebuggerUtilsEx.externalizableEqual(cloned, myOriginalRenderer);
    }

    public void apply() {
        if (myRenderer == null) {
            return;
        }
        flushDataTo(myRenderer);
        // update the renderer to compare with in order to find out whether we've been modified since last apply
        myOriginalRenderer = (CompoundTypeRenderer) myRenderer.clone();
    }

    private void flushDataTo(final CompoundTypeRenderer renderer) { // label
        LabelRenderer labelRenderer = null;
        renderer.setShowType(myShowTypeCheckBox.isSelected());
        if (myRbExpressionLabel.isSelected()) {
            labelRenderer = new LabelRenderer();
            labelRenderer.setLabelExpression(TextWithImportsImpl.fromXExpression(myLabelEditor.getExpression()));
            labelRenderer.setOnDemand(myOnDemandCheckBox.isSelected());
        }
        renderer.setLabelRenderer(labelRenderer);
        // children
        ChildrenRenderer childrenRenderer = null;
        if (myRbExpressionChildrenRenderer.isSelected()) {
            childrenRenderer = new ExpressionChildrenRenderer();
            ((ExpressionChildrenRenderer) childrenRenderer).setChildrenExpression(TextWithImportsImpl.fromXExpression(myChildrenEditor.getExpression()));
            ((ExpressionChildrenRenderer) childrenRenderer).setChildrenExpandable(TextWithImportsImpl.fromXExpression(myChildrenExpandedEditor.getExpression()));
        }
        else if (myRbListChildrenRenderer.isSelected()) {
            EnumerationChildrenRenderer enumerationChildrenRenderer = new EnumerationChildrenRenderer(getTableModel().getExpressions());
            enumerationChildrenRenderer.setAppendDefaultChildren(myAppendDefaultChildren.isSelected());
            childrenRenderer = enumerationChildrenRenderer;
        }
        renderer.setChildrenRenderer(childrenRenderer);
        // classname
        renderer.setClassName(myClassNameField.getText());
    }

    public void reset() {
        final TextWithImports emptyExpressionFragment = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "");
        ((CardLayout) getLayout()).show(this, myRenderer == null ? EMPTY_PANEL_ID : DATA_PANEL_ID);
        if (myRenderer == null) {
            return;
        }
        final String className = myRenderer.getClassName();
        myClassNameField.setText(className);

        updateContext(className);

        final ValueLabelRenderer labelRenderer = myRenderer.getLabelRenderer();
        final ChildrenRenderer childrenRenderer = myRenderer.getChildrenRenderer();

        myShowTypeCheckBox.setSelected(myRenderer.isShowType());

        if (myRenderer.isBaseRenderer(labelRenderer)) {
            myLabelEditor.setExpression(TextWithImportsImpl.toXExpression(emptyExpressionFragment));
            myRbDefaultLabel.setSelected(true);
            myOnDemandCheckBox.setSelected(false);
        }
        else {
            myRbExpressionLabel.setSelected(true);
            LabelRenderer lr = (LabelRenderer) labelRenderer;
            myLabelEditor.setExpression(TextWithImportsImpl.toXExpression(lr.getLabelExpression()));
            myOnDemandCheckBox.setSelected(lr.isOnDemand());
        }

        getTableModel().clear();
        myAppendDefaultChildren.setSelected(false);

        if (myRenderer.isBaseRenderer(childrenRenderer)) {
            myRbDefaultChildrenRenderer.setSelected(true);
            myChildrenEditor.setExpression(TextWithImportsImpl.toXExpression(emptyExpressionFragment));
            myChildrenExpandedEditor.setExpression(TextWithImportsImpl.toXExpression(emptyExpressionFragment));
        }
        else if (childrenRenderer instanceof ExpressionChildrenRenderer) {
            myRbExpressionChildrenRenderer.setSelected(true);
            final ExpressionChildrenRenderer exprRenderer = (ExpressionChildrenRenderer) childrenRenderer;
            myChildrenEditor.setExpression(TextWithImportsImpl.toXExpression(exprRenderer.getChildrenExpression()));
            myChildrenExpandedEditor.setExpression(TextWithImportsImpl.toXExpression(exprRenderer.getChildrenExpandable()));
        }
        else {
            myRbListChildrenRenderer.setSelected(true);
            myChildrenEditor.setExpression(TextWithImportsImpl.toXExpression(emptyExpressionFragment));
            myChildrenExpandedEditor.setExpression(TextWithImportsImpl.toXExpression(emptyExpressionFragment));
            if (childrenRenderer instanceof EnumerationChildrenRenderer) {
                EnumerationChildrenRenderer enumerationRenderer = (EnumerationChildrenRenderer) childrenRenderer;
                getTableModel().init(enumerationRenderer.getChildren());
                myAppendDefaultChildren.setSelected(enumerationRenderer.isAppendDefaultChildren());
            }
        }

        updateEnabledState();
    }

    private MyTableModel getTableModel() {
        return (MyTableModel) myTable.getModel();
    }

    private static final class MyTableModel extends AbstractTableModel {
        private final List<EnumerationChildrenRenderer.ChildInfo> myData = new ArrayList<>();

        public MyTableModel() {
        }

        public void init(List<EnumerationChildrenRenderer.ChildInfo> data) {
            myData.clear();
            for (EnumerationChildrenRenderer.ChildInfo childInfo : data) {
                myData.add(new EnumerationChildrenRenderer.ChildInfo(childInfo.myName, childInfo.myExpression, childInfo.myOnDemand));
            }
            fireTableDataChanged();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public int getRowCount() {
            return myData.size();
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Nonnull
        @Override
        public Class getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case NAME_TABLE_COLUMN:
                    return String.class;
                case EXPRESSION_TABLE_COLUMN:
                    return TextWithImports.class;
                case ONDEMAND_TABLE_COLUMN:
                    return Boolean.class;
                default:
                    return super.getColumnClass(columnIndex);
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= getRowCount()) {
                return null;
            }
            final EnumerationChildrenRenderer.ChildInfo row = myData.get(rowIndex);
            switch (columnIndex) {
                case NAME_TABLE_COLUMN:
                    return row.myName;
                case EXPRESSION_TABLE_COLUMN:
                    return row.myExpression;
                case ONDEMAND_TABLE_COLUMN:
                    return row.myOnDemand;
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (rowIndex >= getRowCount()) {
                return;
            }
            final EnumerationChildrenRenderer.ChildInfo row = myData.get(rowIndex);
            switch (columnIndex) {
                case NAME_TABLE_COLUMN:
                    row.myName = (String) aValue;
                    break;
                case EXPRESSION_TABLE_COLUMN:
                    row.myExpression = (TextWithImports) aValue;
                    break;
                case ONDEMAND_TABLE_COLUMN:
                    row.myOnDemand = (Boolean) aValue;
                    break;
            }
        }

        @Nonnull
        @Override
        public String getColumnName(int columnIndex) {
            switch (columnIndex) {
                case NAME_TABLE_COLUMN:
                    return DebuggerBundle.message("label.compound.renderer.configurable.table.header.name");
                case EXPRESSION_TABLE_COLUMN:
                    return DebuggerBundle.message("label.compound.renderer.configurable.table.header.expression");
                case ONDEMAND_TABLE_COLUMN:
                    return DebuggerBundle.message("label.compound.renderer.configurable.table.header.ondemand");
                default:
                    return "";
            }
        }

        public void addRow(final String name, final TextWithImports expressionWithImports) {
            myData.add(new EnumerationChildrenRenderer.ChildInfo(name, expressionWithImports, false));
            final int lastRow = myData.size() - 1;
            fireTableRowsInserted(lastRow, lastRow);
        }

        public void removeRow(final int row) {
            if (row >= 0 && row < myData.size()) {
                myData.remove(row);
                fireTableRowsDeleted(row, row);
            }
        }

        public void clear() {
            myData.clear();
            fireTableDataChanged();
        }

        public List<EnumerationChildrenRenderer.ChildInfo> getExpressions() {
            return myData;
        }

        private static final class Row {
            public String name;
            public TextWithImports value;

            public Row(final String name, final TextWithImports value) {
                this.name = name;
                this.value = value;
            }
        }
    }

    private static class ClassNameEditorWithBrowseButton extends ReferenceEditorWithBrowseButton {
        private ClassNameEditorWithBrowseButton(ActionListener browseActionListener, final Project project) {
            super(browseActionListener, project, s ->
            {
                JavaCodeFragment fragment = new PsiTypeCodeFragmentImpl(project, true, "fragment.java", s, 0, null) {
                    @Override
                    public boolean importClass(PsiClass aClass) {
                        return false;
                    }
                };
                fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
                return PsiDocumentManager.getInstance(project).getDocument(fragment);
            }, "");
        }
    }
}
