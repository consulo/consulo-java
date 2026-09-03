// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.settings;

import com.intellij.java.debugger.impl.ui.JavaDebuggerSupport;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.language.impl.codeInsight.AnnotationsPanel;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.application.dumb.IndexNotReadyException;
import consulo.application.util.registry.Registry;
import consulo.component.ProcessCanceledException;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.SearchableConfigurable;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserFactory;
import consulo.fileChooser.FileSaverDescriptor;
import consulo.fileChooser.IdeaFileChooser;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.AnActionButton;
import consulo.ui.ex.awt.AnActionButtonRunnable;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.ItemRemovable;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.Splitter;
import consulo.ui.ex.awt.ToolbarDecorator;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.jdom.JDOMUtil;
import consulo.util.xml.serializer.XmlSerializer;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileWrapper;
import jakarta.inject.Inject;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jdom.Document;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public final class CaptureConfigurable implements SearchableConfigurable, ProjectConfigurable, Configurable.NoScroll {
    private static final Logger LOG = Logger.getInstance(CaptureConfigurable.class);
    private final Project myProject;

    private final JCheckBox myDebuggerAgent;
    private final JCheckBox myThrottling;
    private final JButton myConfigureAnnotationsButton;
    private final JPanel myCapturePanel;
    private MyTableModel myTableModel;
    private final JCheckBox myCaptureVariables;
    private final JPanel myPanel;

    @Inject
    public CaptureConfigurable(Project project) {
        myProject = project;

        // plain Swing replacement of the IntelliJ GUI Designer form:
        // row 0: [agent checkbox][configure annotations button][glue]
        // row 1: throttling checkbox (indented)
        // row 2: capture panel filling the rest
        myPanel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        constraints.weighty = 0;
        constraints.insets = JBUI.insets(0);

        myDebuggerAgent = new JBCheckBox(JavaDebuggerLocalize.labelCaptureConfigurableDebuggerAgent().get());
        myPanel.add(myDebuggerAgent, constraints);

        myConfigureAnnotationsButton = new JButton(JavaDebuggerLocalize.labelCaptureConfigurableAnnotationsConfigure().get());
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = JBUI.insets(0, 10, 0, 0);
        myPanel.add(myConfigureAnnotationsButton, constraints);

        constraints.gridx = 2;
        constraints.weightx = 1;
        constraints.insets = JBUI.insets(0);
        myPanel.add(Box.createHorizontalGlue(), constraints);

        myThrottling = new JBCheckBox(JavaDebuggerLocalize.labelCaptureConfigurableThrottling().get());
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 3;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = JBUI.insets(4, 10, 0, 0);
        myPanel.add(myThrottling, constraints);

        myCapturePanel = new JPanel(new BorderLayout(0, 0));
        constraints.gridy = 2;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = JBUI.insets(4, 0, 0, 0);
        myPanel.add(myCapturePanel, constraints);

        myCaptureVariables = new JBCheckBox(JavaDebuggerLocalize.labelCaptureConfigurableCaptureVariables().get());
        myCapturePanel.add(myCaptureVariables, BorderLayout.SOUTH);
    }

    @Override
    public String getId() {
        return "reference.idesettings.debugger.capture";
    }

    @Override
    public @Nullable String getParentId() {
        return "project.propDebugger";
    }

    @Override
    public String getHelpTopic() {
        return getId();
    }

    @RequiredUIAccess
    @Override
    public @Nullable JComponent createComponent() {
        myConfigureAnnotationsButton.addActionListener(e -> new AsyncAnnotationsDialog(myProject).showAsync());

        myDebuggerAgent.addChangeListener(e -> setThrottlingCheckboxEnabled());
        setThrottlingCheckboxEnabled();

        myTableModel = new MyTableModel();

        boolean breakpointsEnabled = Registry.is("debugger.async.stacks.via.breakpoints", false);
        myCaptureVariables.setVisible(breakpointsEnabled);
        if (!breakpointsEnabled) {
            return myPanel;
        }

        JBTable table = new JBTable(myTableModel);
        table.setColumnSelectionAllowed(false);
        table.setShowGrid(false);

        JTextField stringCellEditor = new JTextField();
        table.setDefaultEditor(String.class, new DefaultCellEditor(stringCellEditor));
        table.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                Dimension editorSize = stringCellEditor.getPreferredSize();
                size.height = Math.max(size.height, editorSize.height);
                return size;
            }
        });

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

        decorator.addExtraAction(new CapturePointsActionButton(
            JavaDebuggerLocalize.actionAnactionbuttonTextDuplicate(),
            JavaDebuggerLocalize.actionAnactionbuttonDescriptionDuplicate(),
            PlatformIconGroup.actionsCopy()
        ) {
            @Override
            public void updateButton(AnActionEvent e) {
                e.getPresentation().setEnabled(table.getSelectedRowCount() == 1);
            }

            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {
                selectedCapturePoints(table).forEach(c -> {
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

        decorator.addExtraAction(new CapturePointsActionButton(
            JavaDebuggerLocalize.actionAnactionbuttonTextEnableSelected(),
            JavaDebuggerLocalize.actionAnactionbuttonDescriptionEnableSelected(),
            PlatformIconGroup.actionsSelectall()
        ) {
            @Override
            public void updateButton(AnActionEvent e) {
                e.getPresentation().setEnabled(table.getSelectedRowCount() > 0);
            }

            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {
                selectedCapturePoints(table).forEach(c -> c.myEnabled = true);
                table.repaint();
            }
        });
        decorator.addExtraAction(new CapturePointsActionButton(
            JavaDebuggerLocalize.actionAnactionbuttonTextDisableSelected(),
            JavaDebuggerLocalize.actionAnactionbuttonDescriptionDisableSelected(),
            PlatformIconGroup.actionsUnselectall()
        ) {
            @Override
            public void updateButton(AnActionEvent e) {
                e.getPresentation().setEnabled(table.getSelectedRowCount() > 0);
            }

            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {
                selectedCapturePoints(table).forEach(c -> c.myEnabled = false);
                table.repaint();
            }
        });

        new CapturePointsActionButton(JavaDebuggerLocalize.actionTextToggle()) {
            @Override
            public void updateButton(AnActionEvent e) {
                e.getPresentation().setEnabled(table.getSelectedRowCount() == 1 && !table.isEditing());
            }

            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {
                selectedCapturePoints(table).forEach(c -> c.myEnabled = !c.myEnabled);
                table.repaint();
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), table);

        decorator.addExtraAction(new DumbAwareAction(
            JavaDebuggerLocalize.actionAnactionbuttonTextImport(),
            JavaDebuggerLocalize.actionAnactionbuttonDescriptionImport(),
            PlatformIconGroup.actionsInstall()
        ) {
            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {
                FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, false, true, true)
                    .withExtensionFilter("xml")
                    .withTitle(JavaDebuggerLocalize.importCapturePoints())
                    .withDescription(JavaDebuggerLocalize.pleaseSelectAFileToImport());

                VirtualFile[] files = IdeaFileChooser.chooseFiles(descriptor, e.getData(Project.KEY), null);
                if (ArrayUtil.isEmpty(files)) {
                    return;
                }

                table.getSelectionModel().clearSelection();

                for (VirtualFile file : files) {
                    try {
                        for (Element element : JDOMUtil.load(file.getInputStream()).getChildren()) {
                            int idx = myTableModel.addIfNeeded(XmlSerializer.deserialize(element, CapturePoint.class));
                            table.getSelectionModel().addSelectionInterval(idx, idx);
                        }
                    }
                    catch (Exception ex) {
                        String msg = ex.getLocalizedMessage();
                        Messages.showErrorDialog(
                            e.getData(Project.KEY),
                            msg != null && !msg.isEmpty() ? msg : ex.toString(),
                            JavaDebuggerLocalize.exportFailed().get()
                        );
                    }
                }
            }
        });
        decorator.addExtraAction(new CapturePointsActionButton(
            JavaDebuggerLocalize.actionAnactionbuttonTextExport(),
            JavaDebuggerLocalize.actionAnactionbuttonDescriptionExport(),
            PlatformIconGroup.actionsExport()
        ) {
            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {
                VirtualFileWrapper wrapper = FileChooserFactory.getInstance()
                    .createSaveFileDialog(
                        new FileSaverDescriptor(JavaDebuggerLocalize.exportSelectedCapturePointsToFile().get(), "", "xml"),
                        e.getData(Project.KEY)
                    )
                    .save(null, null);
                if (wrapper == null) {
                    return;
                }

                Element rootElement = new Element("capture-points");
                selectedCapturePoints(table).forEach(c -> {
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
                    String msg = ex.getLocalizedMessage();
                    Messages.showErrorDialog(
                        e.getData(Project.KEY),
                        msg != null && !msg.isEmpty() ? msg : ex.toString(),
                        JavaDebuggerLocalize.exportFailed().get()
                    );
                }
            }

            @Override
            public void updateButton(AnActionEvent e) {
                e.getPresentation().setEnabled(table.getSelectedRowCount() > 0);
            }
        });

        myCapturePanel.setBorder(
            IdeBorderFactory.createTitledBorder(JavaDebuggerLocalize.settingsBreakpointsBased().get(), false, JBUI.insetsTop(8))
                .setShowLine(false)
        );
        myCapturePanel.add(decorator.createPanel(), BorderLayout.CENTER);

        return myPanel;
    }

    private void setThrottlingCheckboxEnabled() {
        myThrottling.setEnabled(myDebuggerAgent.isSelected());
    }

    /**
     * IDEA expresses the table actions as {@code DumbAwareAction}s overriding {@code update}. Consulo's {@code AnAction} has no
     * {@code update} hook; toolbar actions get it from {@link AnActionButton#updateButton(AnActionEvent)}, so the same actions are
     * modelled as dumb-aware action buttons here.
     */
    private abstract static class CapturePointsActionButton extends AnActionButton implements DumbAware {
        CapturePointsActionButton(LocalizeValue text) {
            super(text);
        }

        CapturePointsActionButton(LocalizeValue text, LocalizeValue description, @Nullable Image icon) {
            super(text, description, icon);
        }
    }

    private StreamEx<CapturePoint> selectedCapturePoints(JBTable table) {
        return IntStreamEx.of(table.getSelectedRows()).map(table::convertRowIndexToModel).mapToObj(myTableModel::get);
    }

    private static final class MyTableModel extends AbstractTableModel implements ItemRemovable {
        public static final int ENABLED_COLUMN = 0;
        public static final int CLASS_COLUMN = 1;
        public static final int METHOD_COLUMN = 2;
        public static final int PARAM_COLUMN = 3;
        public static final int INSERT_CLASS_COLUMN = 4;
        public static final int INSERT_METHOD_COLUMN = 5;
        public static final int INSERT_KEY_EXPR = 6;

        static final String[] COLUMN_NAMES = getColumns();

        private static String[] getColumns() {
            return new String[]{
                "",
                JavaDebuggerLocalize.settingsCaptureColumnCaptureClassName().get(),
                JavaDebuggerLocalize.settingsCaptureColumnCaptureMethodName().get(),
                JavaDebuggerLocalize.settingsCaptureColumnCaptureKeyExpression().get(),
                JavaDebuggerLocalize.settingsCaptureColumnInsertClassName().get(),
                JavaDebuggerLocalize.settingsCaptureColumnInsertMethodName().get(),
                JavaDebuggerLocalize.settingsCaptureColumnInsertKeyExpression().get()
            };
        }

        List<CapturePoint> myCapturePoints;

        private MyTableModel() {
            myCapturePoints = DebuggerSettings.getInstance().cloneCapturePoints();
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
            return switch (col) {
                case ENABLED_COLUMN -> point.myEnabled;
                case CLASS_COLUMN -> point.myClassName;
                case METHOD_COLUMN -> point.myMethodName;
                case PARAM_COLUMN -> point.myCaptureKeyExpression;
                case INSERT_CLASS_COLUMN -> point.myInsertClassName;
                case INSERT_METHOD_COLUMN -> point.myInsertMethodName;
                case INSERT_KEY_EXPR -> point.myInsertKeyExpression;
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            CapturePoint point = myCapturePoints.get(row);
            switch (col) {
                case ENABLED_COLUMN -> point.myEnabled = (boolean) value;
                case CLASS_COLUMN -> point.myClassName = (String) value;
                case METHOD_COLUMN -> point.myMethodName = (String) value;
                case PARAM_COLUMN -> point.myCaptureKeyExpression = (String) value;
                case INSERT_CLASS_COLUMN -> point.myInsertClassName = (String) value;
                case INSERT_METHOD_COLUMN -> point.myInsertMethodName = (String) value;
                case INSERT_KEY_EXPR -> point.myInsertKeyExpression = (String) value;
            }
            fireTableCellUpdated(row, col);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == ENABLED_COLUMN ? Boolean.class : String.class;
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
        public void removeRow(int row) {
            myCapturePoints.remove(row);
            fireTableRowsDeleted(row, row);
        }
    }

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        return DebuggerSettings.getInstance().CAPTURE_VARIABLES != myCaptureVariables.isSelected() ||
            DebuggerSettings.getInstance().INSTRUMENTING_AGENT != myDebuggerAgent.isSelected() ||
            DebuggerSettings.getInstance().AGENT_THROTTLING != myThrottling.isSelected() ||
            !DebuggerSettings.getInstance().getCapturePoints().equals(myTableModel.myCapturePoints);
    }

    @RequiredUIAccess
    @Override
    public void apply() throws ConfigurationException {
        DebuggerSettings.getInstance().setCapturePoints(myTableModel.myCapturePoints);
        DebuggerSettings.getInstance().CAPTURE_VARIABLES = myCaptureVariables.isSelected();
        DebuggerSettings.getInstance().INSTRUMENTING_AGENT = myDebuggerAgent.isSelected();
        DebuggerSettings.getInstance().AGENT_THROTTLING = myThrottling.isSelected();
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        myCaptureVariables.setSelected(DebuggerSettings.getInstance().CAPTURE_VARIABLES);
        myDebuggerAgent.setSelected(DebuggerSettings.getInstance().INSTRUMENTING_AGENT);
        myThrottling.setSelected(DebuggerSettings.getInstance().AGENT_THROTTLING);
        myTableModel.myCapturePoints = DebuggerSettings.getInstance().cloneCapturePoints();
        myTableModel.fireTableDataChanged();
    }

    @Override
    public LocalizeValue getDisplayName() {
        return JavaDebuggerLocalize.asyncStacktracesConfigurableDisplayName();
    }

    interface CapturePointConsumer<R> {
        R accept(boolean capture, PsiModifierListOwner e, PsiAnnotation annotation);
    }

    static <R> List<R> processCaptureAnnotations(@Nullable Project project, CapturePointConsumer<R> consumer) {
        Project contextProject = project;
        if (contextProject == null) { // fallback
            contextProject = JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables();
        }
        if (contextProject.isDefault()) {
            return Collections.emptyList();
        }
        DebuggerProjectSettings debuggerProjectSettings = DebuggerProjectSettings.getInstance(contextProject);
        return ContainerUtil.concat(
            scanPointsInt(contextProject, debuggerProjectSettings, true, consumer),
            scanPointsInt(contextProject, debuggerProjectSettings, false, consumer)
        );
    }

    private static <R> List<R> scanPointsInt(
        Project project,
        DebuggerProjectSettings debuggerProjectSettings,
        boolean capture,
        CapturePointConsumer<R> consumer
    ) {
        try {
            return NodeRendererSettings.visitAnnotatedElements(
                getAsyncAnnotations(debuggerProjectSettings, capture),
                project,
                (e, annotation) -> consumer.accept(capture, e, annotation),
                PsiMethod.class,
                PsiParameter.class
            );
        }
        catch (IndexNotReadyException | ProcessCanceledException ignore) {
        }
        catch (Exception e) {
            LOG.error(e);
        }
        return Collections.emptyList();
    }

    static String getAnnotationName(boolean capture) {
        // Consulo's classpath has no org.jetbrains.annotations.Async, so the JB names are kept as string constants
        return capture ? "org.jetbrains.annotations.Async.Schedule" : "org.jetbrains.annotations.Async.Execute";
    }

    private static List<String> getAsyncAnnotations(DebuggerProjectSettings debuggerProjectSettings, boolean capture) {
        return StreamEx.of(capture ? debuggerProjectSettings.myAsyncScheduleAnnotations : debuggerProjectSettings.myAsyncExecuteAnnotations)
            .prepend(getAnnotationName(capture))
            .toList();
    }

    private final class AsyncAnnotationsDialog extends DialogWrapper {
        private final AnnotationsPanel myAsyncSchedulePanel;
        private final AnnotationsPanel myAsyncExecutePanel;
        private final DebuggerProjectSettings mySettings;

        private AsyncAnnotationsDialog(Project project) {
            super(project, true);
            mySettings = DebuggerProjectSettings.getInstance(myProject);
            myAsyncSchedulePanel = new AnnotationsPanel(
                project,
                JavaDebuggerLocalize.settingsAsyncSchedule().get(),
                getAsyncAnnotations(mySettings, true),
                Collections.singletonList(getAnnotationName(true))
            );
            myAsyncExecutePanel = new AnnotationsPanel(
                project,
                JavaDebuggerLocalize.settingsAsyncExecute().get(),
                getAsyncAnnotations(mySettings, false),
                Collections.singletonList(getAnnotationName(false))
            );
            init();
            setTitle(JavaDebuggerLocalize.settingsAsyncAnnotationsConfiguration());
        }

        @Override
        protected JComponent createCenterPanel() {
            Splitter splitter = new Splitter(true);
            splitter.setFirstComponent(myAsyncSchedulePanel.getComponent());
            splitter.setSecondComponent(myAsyncExecutePanel.getComponent());
            splitter.setHonorComponentsMinimumSize(true);
            splitter.setPreferredSize(JBUI.size(300, 400));
            return splitter;
        }

        @Override
        protected void doOKAction() {
            mySettings.myAsyncScheduleAnnotations = StreamEx.of(myAsyncSchedulePanel.getAnnotations())
                .filter(e -> !e.equals(getAnnotationName(true)))
                .toArray(ArrayUtil.EMPTY_STRING_ARRAY);
            mySettings.myAsyncExecuteAnnotations = StreamEx.of(myAsyncExecutePanel.getAnnotations())
                .filter(e -> !e.equals(getAnnotationName(false)))
                .toArray(ArrayUtil.EMPTY_STRING_ARRAY);
            super.doOKAction();
        }

        @Override
        protected @Nullable String getHelpId() {
            return "reference.idesettings.debugger.customAsyncAnnotations";
        }
    }
}
