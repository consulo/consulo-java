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
import com.intellij.java.debugger.impl.engine.JVMNameUtil;
import com.intellij.java.debugger.impl.jdi.DecompiledLocalVariable;
import com.intellij.java.debugger.impl.ui.JavaDebuggerSupport;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.indexing.search.searches.AnnotatedElementsSearch;
import com.intellij.java.language.psi.*;
import consulo.application.AllIcons;
import consulo.application.dumb.IndexNotReadyException;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserFactory;
import consulo.fileChooser.FileSaverDescriptor;
import consulo.fileChooser.IdeaFileChooser;
import consulo.ide.impl.idea.ui.DumbAwareActionButton;
import consulo.java.debugger.impl.JavaRegistry;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.jdom.JDOMUtil;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.XmlSerializer;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileWrapper;
import consulo.virtualFileSystem.archive.ArchiveFileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.Debugger;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author egor
 */
public class CaptureConfigurable implements SearchableConfigurable {
    private static final Logger LOG = Logger.getInstance(CaptureConfigurable.class);

    private MyTableModel myTableModel;
    private JCheckBox myCaptureVariables;

    @Nonnull
    @Override
    public String getId() {
        return "reference.idesettings.debugger.capture";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        myTableModel = new MyTableModel();

        JBTable table = new JBTable(myTableModel);
        table.setColumnSelectionAllowed(false);

        TableColumnModel columnModel = table.getColumnModel();
        TableUtil.setupCheckboxColumn(columnModel.getColumn(MyTableModel.ENABLED_COLUMN));

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table);
        decorator.setAddAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
                myTableModel.addRow();
            }
        });
        decorator.setRemoveAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
                TableUtil.removeSelectedItems(table);
            }
        });
        decorator.setMoveUpAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
                TableUtil.moveSelectedItemsUp(table);
            }
        });
        decorator.setMoveDownAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
                TableUtil.moveSelectedItemsDown(table);
            }
        });

        decorator.addExtraAction(new DumbAwareActionButton(LocalizeValue.of("Duplicate"), LocalizeValue.of("Duplicate"), AllIcons.Actions.Copy) {
            @Override
            public boolean isEnabled() {
                return table.getSelectedRowCount() == 1;
            }

            @Override
            public void actionPerformed(@Nonnull AnActionEvent e) {
                selectedCapturePoints(table).forEach(c ->
                {
                    try {
                        int idx = myTableModel.add(c.clone());
                        table.getSelectionModel().setSelectionInterval(idx, idx);
                    }
                    catch (CloneNotSupportedException ex) {
                        LOG.error(ex);
                    }
                });
            }
        });

        decorator.addExtraAction(new DumbAwareActionButton(LocalizeValue.of("Enable Selected"), LocalizeValue.of("Enable Selected"), AllIcons.Actions.Selectall) {
            @Override
            public boolean isEnabled() {
                return table.getSelectedRowCount() > 0;
            }

            @Override
            public void actionPerformed(@Nonnull AnActionEvent e) {
                selectedCapturePoints(table).forEach(c -> c.myEnabled = true);
                table.repaint();
            }
        });
        decorator.addExtraAction(new DumbAwareActionButton(LocalizeValue.of("Disable Selected"), LocalizeValue.of("Disable Selected"), AllIcons.Actions.Unselectall) {
            @Override
            public boolean isEnabled() {
                return table.getSelectedRowCount() > 0;
            }

            @Override
            public void actionPerformed(@Nonnull AnActionEvent e) {
                selectedCapturePoints(table).forEach(c -> c.myEnabled = false);
                table.repaint();
            }
        });

        new DumbAwareAction("Toggle") {
            @Override
            public void update(@Nonnull AnActionEvent e) {
                e.getPresentation().setEnabled(table.getSelectedRowCount() == 1);
            }

            @Override
            public void actionPerformed(@Nonnull final AnActionEvent e) {
                selectedCapturePoints(table).forEach(c -> c.myEnabled = !c.myEnabled);
                table.repaint();
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), table);

        decorator.addExtraAction(new DumbAwareActionButton(LocalizeValue.of("Import"), LocalizeValue.of("Import"), AllIcons.Actions.Install) {
            @Override
            public void actionPerformed(@Nonnull final AnActionEvent e) {
                FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, false, true, true) {
                    @Override
                    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
                        return super.isFileVisible(file, showHiddenFiles) && (file.isDirectory() || "xml".equals(file.getExtension()) || file.getFileType() instanceof ArchiveFileType);
                    }

                    @Override
                    public boolean isFileSelectable(VirtualFile file) {
                        return "xml".equals(file.getExtension());
                    }
                };
                descriptor.setDescription("Please select a file to import.");
                descriptor.setTitle("Import Capture Points");

                VirtualFile[] files = IdeaFileChooser.chooseFiles(descriptor, e.getData(Project.KEY), null);
                if (ArrayUtil.isEmpty(files)) {
                    return;
                }

                table.getSelectionModel().clearSelection();

                for (VirtualFile file : files) {
                    try {
                        Document document = JDOMUtil.loadDocument(file.getInputStream());
                        List<Element> children = document.getRootElement().getChildren();
                        children.forEach(element ->
                        {
                            int idx = myTableModel.addIfNeeded(XmlSerializer.deserialize(element, CapturePoint.class));
                            table.getSelectionModel().addSelectionInterval(idx, idx);
                        });
                    }
                    catch (Exception ex) {
                        final String msg = ex.getLocalizedMessage();
                        Messages.showErrorDialog(e.getData(Project.KEY), msg != null && msg.length() > 0 ? msg : ex.toString(), "Export Failed");
                    }
                }
            }
        });
        decorator.addExtraAction(new DumbAwareActionButton(LocalizeValue.of("Export"), LocalizeValue.of("Export"), AllIcons.Actions.Export) {
            @Override
            public void actionPerformed(@Nonnull final AnActionEvent e) {
                VirtualFileWrapper wrapper = FileChooserFactory.getInstance().createSaveFileDialog(new FileSaverDescriptor("Export Selected Capture Points to File...", "", "xml"), e.getData(Project.KEY))
                    .save(null, null);
                if (wrapper == null) {
                    return;
                }

                Element rootElement = new Element("capture-points");
                selectedCapturePoints(table).forEach(c ->
                {
                    try {
                        CapturePoint clone = c.clone();
                        clone.myEnabled = false;
                        rootElement.addContent(XmlSerializer.serialize(clone));
                    }
                    catch (CloneNotSupportedException ex) {
                        LOG.error(ex);
                    }
                });
                try {
                    JDOMUtil.writeDocument(new Document(rootElement), wrapper.getFile(), "\n");
                }
                catch (Exception ex) {
                    final String msg = ex.getLocalizedMessage();
                    Messages.showErrorDialog(e.getData(Project.KEY), msg != null && msg.length() > 0 ? msg : ex.toString(), "Export Failed");
                }
            }

            @Override
            public boolean isEnabled() {
                return table.getSelectedRowCount() > 0;
            }
        });

        BorderLayoutPanel panel = JBUI.Panels.simplePanel();
        panel.addToCenter(decorator.createPanel());

        myCaptureVariables = new JCheckBox(DebuggerBundle.message("label.capture.configurable.capture.variables"));
        panel.addToBottom(myCaptureVariables);
        return panel;
    }

    private List<CapturePoint> selectedCapturePoints(JBTable table) {
        List<CapturePoint> list = new ArrayList<>();
        for (int row : table.getSelectedRows()) {
            int tableModel = table.convertRowIndexToModel(row);
            list.add(myTableModel.get(tableModel));
        }
        return list;
    }

    private static class MyTableModel extends AbstractTableModel implements ItemRemovable {
        public static final int ENABLED_COLUMN = 0;
        public static final int CLASS_COLUMN = 1;
        public static final int METHOD_COLUMN = 2;
        public static final int PARAM_COLUMN = 3;
        public static final int INSERT_CLASS_COLUMN = 4;
        public static final int INSERT_METHOD_COLUMN = 5;
        public static final int INSERT_KEY_EXPR = 6;

        static final String[] COLUMN_NAMES = new String[]{
            "",
            "Capture class name",
            "Capture method name",
            "Capture key expression",
            "Insert class name",
            "Insert method name",
            "Insert key expression"
        };
        List<CapturePoint> myCapturePoints;

        private MyTableModel() {
            myCapturePoints = DebuggerSettings.getInstance().cloneCapturePoints();
            scanPoints();
        }

        private void scanPoints() {
            if (JavaRegistry.DEBUGGER_CAPTURE_POINTS_ANNOTATIONS) {
                List<CapturePoint> capturePointsFromAnnotations = new ArrayList<>();
                scanPointsInt(true, capturePointsFromAnnotations);
                scanPointsInt(false, capturePointsFromAnnotations);

                capturePointsFromAnnotations.forEach(this::addIfNeeded);
            }
        }

        private static void scanPointsInt(boolean capture, List<CapturePoint> capturePointsFromAnnotations) {
            try {
                String annotationName = (capture ? Debugger.Capture.class : Debugger.Insert.class).getName().replace("$", ".");
                Project project = JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables();
                GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
                PsiClass annotationClass = JavaPsiFacade.getInstance(project).findClass(annotationName, allScope);
                if (annotationClass != null) {
                    AnnotatedElementsSearch.searchElements(annotationClass, allScope, PsiMethod.class, PsiParameter.class).forEach(e ->
                    {
                        if (e instanceof PsiMethod) {
                            addCapturePointIfNeeded(e, (PsiMethod) e, annotationName, "this", capture, capturePointsFromAnnotations);
                        }
                        else if (e instanceof PsiParameter) {
                            PsiParameter psiParameter = (PsiParameter) e;
                            PsiMethod psiMethod = (PsiMethod) psiParameter.getDeclarationScope();
                            addCapturePointIfNeeded(psiParameter, psiMethod, annotationName, DecompiledLocalVariable.PARAM_PREFIX + psiMethod.getParameterList().getParameterIndex(psiParameter),
                                capture, capturePointsFromAnnotations);
                        }
                    });
                }
            }
            catch (IndexNotReadyException ignore) {
            }
            catch (Exception e) {
                LOG.error(e);
            }
        }

        private static void addCapturePointIfNeeded(PsiModifierListOwner psiElement,
                                                    PsiMethod psiMethod,
                                                    String annotationName,
                                                    String defaultExpression,
                                                    boolean capture,
                                                    List<CapturePoint> capturePointsFromAnnotations) {
            CapturePoint capturePoint = new CapturePoint();
            capturePoint.myEnabled = false;
            if (capture) {
                capturePoint.myClassName = JVMNameUtil.getNonAnonymousClassName(psiMethod.getContainingClass());
                capturePoint.myMethodName = JVMNameUtil.getJVMMethodName(psiMethod);
            }
            else {
                capturePoint.myInsertClassName = JVMNameUtil.getNonAnonymousClassName(psiMethod.getContainingClass());
                capturePoint.myInsertMethodName = JVMNameUtil.getJVMMethodName(psiMethod);
            }

            PsiModifierList modifierList = psiElement.getModifierList();
            if (modifierList != null) {
                PsiAnnotation annotation = modifierList.findAnnotation(annotationName);
                if (annotation != null) {
                    PsiAnnotationMemberValue keyExpressionValue = annotation.findAttributeValue("keyExpression");
                    String keyExpression = keyExpressionValue != null ? StringUtil.unquoteString(keyExpressionValue.getText()) : null;
                    if (StringUtil.isEmpty(keyExpression)) {
                        keyExpression = defaultExpression;
                    }
                    if (capture) {
                        capturePoint.myCaptureKeyExpression = keyExpression;
                    }
                    else {
                        capturePoint.myInsertKeyExpression = keyExpression;
                    }

                    PsiAnnotationMemberValue groupValue = annotation.findAttributeValue("group");
                    String group = groupValue != null ? StringUtil.unquoteString(groupValue.getText()) : null;
                    if (!StringUtil.isEmpty(group)) {
                        for (CapturePoint capturePointsFromAnnotation : capturePointsFromAnnotations) {
                            if (StringUtil.startsWith(group, capturePointsFromAnnotation.myClassName) && StringUtil.endsWith(group, capturePointsFromAnnotation.myMethodName)) {
                                capturePointsFromAnnotation.myInsertClassName = capturePoint.myInsertClassName;
                                capturePointsFromAnnotation.myInsertMethodName = capturePoint.myInsertMethodName;
                                capturePointsFromAnnotation.myInsertKeyExpression = capturePoint.myInsertKeyExpression;
                                return;
                            }
                        }
                    }
                }
            }

            capturePointsFromAnnotations.add(capturePoint);
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public int getRowCount() {
            return myCapturePoints.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public Object getValueAt(int row, int col) {
            CapturePoint point = myCapturePoints.get(row);
            switch (col) {
                case ENABLED_COLUMN:
                    return point.myEnabled;
                case CLASS_COLUMN:
                    return point.myClassName;
                case METHOD_COLUMN:
                    return point.myMethodName;
                case PARAM_COLUMN:
                    return point.myCaptureKeyExpression;
                case INSERT_CLASS_COLUMN:
                    return point.myInsertClassName;
                case INSERT_METHOD_COLUMN:
                    return point.myInsertMethodName;
                case INSERT_KEY_EXPR:
                    return point.myInsertKeyExpression;
            }
            return null;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            CapturePoint point = myCapturePoints.get(row);
            switch (col) {
                case ENABLED_COLUMN:
                    point.myEnabled = (boolean) value;
                    break;
                case CLASS_COLUMN:
                    point.myClassName = (String) value;
                    break;
                case METHOD_COLUMN:
                    point.myMethodName = (String) value;
                    break;
                case PARAM_COLUMN:
                    point.myCaptureKeyExpression = (String) value;
                    break;
                case INSERT_CLASS_COLUMN:
                    point.myInsertClassName = (String) value;
                    break;
                case INSERT_METHOD_COLUMN:
                    point.myInsertMethodName = (String) value;
                    break;
                case INSERT_KEY_EXPR:
                    point.myInsertKeyExpression = (String) value;
                    break;
            }
            fireTableCellUpdated(row, col);
        }

        @Override
        public Class getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case ENABLED_COLUMN:
                    return Boolean.class;
            }
            return String.class;
        }

        CapturePoint get(int idx) {
            return myCapturePoints.get(idx);
        }

        int add(CapturePoint p) {
            myCapturePoints.add(p);
            int lastRow = getRowCount() - 1;
            fireTableRowsInserted(lastRow, lastRow);
            return lastRow;
        }

        int addIfNeeded(CapturePoint p) {
            CapturePoint clone = p;
            try {
                clone = p.clone();
                clone.myEnabled = !clone.myEnabled;
            }
            catch (CloneNotSupportedException e) {
                LOG.error(e);
            }
            int idx = myCapturePoints.indexOf(p);
            if (idx < 0) {
                idx = myCapturePoints.indexOf(clone);
            }
            if (idx < 0) {
                idx = add(p);
            }
            return idx;
        }

        public void addRow() {
            add(new CapturePoint());
        }

        @Override
        public void removeRow(final int row) {
            myCapturePoints.remove(row);
            fireTableRowsDeleted(row, row);
        }
    }

    @Override
    public boolean isModified() {
        return DebuggerSettings.getInstance().CAPTURE_VARIABLES != myCaptureVariables.isSelected() || !DebuggerSettings.getInstance().getCapturePoints().equals(myTableModel.myCapturePoints);
    }

    @Override
    public void apply() throws ConfigurationException {
        DebuggerSettings.getInstance().setCapturePoints(myTableModel.myCapturePoints);
        DebuggerSettings.getInstance().CAPTURE_VARIABLES = myCaptureVariables.isSelected();
    }

    @Override
    public void reset() {
        myCaptureVariables.setSelected(DebuggerSettings.getInstance().CAPTURE_VARIABLES);
        myTableModel.myCapturePoints = DebuggerSettings.getInstance().cloneCapturePoints();
        myTableModel.scanPoints();
        myTableModel.fireTableDataChanged();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JavaDebuggerLocalize.asyncStacktracesConfigurableDisplayName();
    }
}
