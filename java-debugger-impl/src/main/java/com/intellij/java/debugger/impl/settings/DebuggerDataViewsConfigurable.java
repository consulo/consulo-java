/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.classFilter.ClassFilterEditor;
import com.intellij.java.debugger.impl.ui.JavaDebuggerSupport;
import com.intellij.java.debugger.impl.ui.tree.render.ClassRenderer;
import com.intellij.java.debugger.impl.ui.tree.render.PrimitiveRenderer;
import com.intellij.java.debugger.impl.ui.tree.render.ToStringRenderer;
import consulo.configurable.SearchableConfigurable;
import consulo.java.language.localize.JavaLanguageLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.StateRestoringCheckBox;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Eugene Belyaev
 */
public class DebuggerDataViewsConfigurable implements SearchableConfigurable {
    private JCheckBox myCbAutoscroll;
    private JCheckBox myCbShowSyntheticFields;
    private StateRestoringCheckBox myCbShowValFieldsAsLocalVariables;
    private JCheckBox myCbHideNullArrayElements;
    private JCheckBox myCbShowDeclaredType;
    private JCheckBox myCbShowFQNames;
    private JCheckBox myCbShowObjectId;
    private JCheckBox myCbShowStringsType;
    private JCheckBox myCbHexValue;

    private JCheckBox myCbEnableAlternateViews;

    private JCheckBox myCbEnableToString;
    private JRadioButton myRbAllThatOverride;
    private JRadioButton myRbFromList;
    private ClassFilterEditor myToStringFilterEditor;

    private Project myProject;

    public DebuggerDataViewsConfigurable(@Nullable Project project) {
        myProject = project;
    }

    @RequiredUIAccess
    @Override
    public void disposeUIResources() {
        myToStringFilterEditor = null;
        myProject = null;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JavaLanguageLocalize.javaLanguageDisplayName();
    }

    @RequiredUIAccess
    @Override
    public JComponent createComponent() {
        if (myProject == null) {
            myProject = JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables();
        }
        final JPanel panel = new JPanel(new GridBagLayout());

        myCbAutoscroll = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.autoscroll"));
        myCbShowSyntheticFields = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.synthetic.fields"));
        myCbShowValFieldsAsLocalVariables = new StateRestoringCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.val.fields.as.locals"));
        myCbHideNullArrayElements = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.hide.null.array.elements"));
        myCbEnableAlternateViews = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.alternate.view"));
        myCbShowSyntheticFields.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (myCbShowSyntheticFields.isSelected()) {
                    myCbShowValFieldsAsLocalVariables.makeSelectable();
                }
                else {
                    myCbShowValFieldsAsLocalVariables.makeUnselectable(false);
                }
            }
        });
        myCbShowDeclaredType = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.declared.type"));
        myCbShowFQNames = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.fq.names"));
        myCbShowObjectId = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.object.id"));
        myCbHexValue = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.hex.value"));
        myCbShowStringsType = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.strings.type"));

        myCbEnableToString = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.enable.toString"));
        myRbAllThatOverride = new JRadioButton(DebuggerBundle.message("label.base.renderer.configurable.all.overriding"));
        myRbFromList = new JRadioButton(DebuggerBundle.message("label.base.renderer.configurable.classes.from.list"));
        ButtonGroup group = new ButtonGroup();
        group.add(myRbAllThatOverride);
        group.add(myRbFromList);
        myToStringFilterEditor = new ClassFilterEditor(myProject, null, "reference.viewBreakpoints.classFilters.newPattern");
        myCbEnableToString.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                final boolean enabled = myCbEnableToString.isSelected();
                myRbAllThatOverride.setEnabled(enabled);
                myRbFromList.setEnabled(enabled);
                myToStringFilterEditor.setEnabled(enabled && myRbFromList.isSelected());
            }
        });
        myRbFromList.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                myToStringFilterEditor.setEnabled(myCbEnableToString.isSelected() && myRbFromList.isSelected());
            }
        });

        panel.add(myCbAutoscroll, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0));


        final JPanel showPanel = new JPanel(new GridBagLayout());
        showPanel.setBorder(IdeBorderFactory.createTitledBorder("Show", true));

        showPanel.add(myCbShowDeclaredType, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        showPanel.add(myCbShowObjectId, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0));
        showPanel.add(myCbShowSyntheticFields, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 10, 0, 0), 0,
            0));
        showPanel.add(myCbShowValFieldsAsLocalVariables, new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 10, 0,
            0), 0, 0));
        showPanel.add(myCbShowFQNames, new GridBagConstraints(3, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 10, 0, 0), 0, 0));

        panel.add(showPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0));

        //final JPanel arraysPanel = new JPanel(new BorderLayout(0, UIUtil.DEFAULT_VGAP));
        //final JComponent arraysComponent = myArrayRendererConfigurable.createComponent();
        //assert arraysComponent != null;
        //arraysPanel.add(arraysComponent, BorderLayout.CENTER);
        //arraysPanel.add(myCbHideNullArrayElements, BorderLayout.SOUTH);
        //arraysPanel.setBorder(IdeBorderFactory.createTitledBorder("Arrays", true));
        //panel.add(arraysPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        panel.add(myCbShowStringsType, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        panel.add(myCbHexValue, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0));
        panel.add(myCbHideNullArrayElements, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0),
            0, 0));

        panel.add(myCbEnableAlternateViews, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 0, 0, 10), 0, 0));
        // starting 4-th row
        panel.add(myCbEnableToString, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0));
        panel.add(myRbAllThatOverride, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 12, 0, 0), 0, 0));
        panel.add(myRbFromList, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 12, 0, 0), 0, 0));
        myToStringFilterEditor.setMinimumSize(JBUI.size(50, 100));
        panel.add(myToStringFilterEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 12, 0, 0), 0, 0));

        return panel;
    }

    @RequiredUIAccess
    @Override
    public void apply() {
        final ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
        final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

        generalSettings.AUTOSCROLL_TO_NEW_LOCALS = myCbAutoscroll.isSelected();
        rendererSettings.setAlternateCollectionViewsEnabled(myCbEnableAlternateViews.isSelected());
        generalSettings.HIDE_NULL_ARRAY_ELEMENTS = myCbHideNullArrayElements.isSelected();

        final ClassRenderer classRenderer = rendererSettings.getClassRenderer();
        classRenderer.SHOW_SYNTHETICS = myCbShowSyntheticFields.isSelected();
        classRenderer.SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES = myCbShowValFieldsAsLocalVariables.isSelectedWhenSelectable();
        classRenderer.SHOW_DECLARED_TYPE = myCbShowDeclaredType.isSelected();
        classRenderer.SHOW_FQ_TYPE_NAMES = myCbShowFQNames.isSelected();
        classRenderer.SHOW_OBJECT_ID = myCbShowObjectId.isSelected();
        classRenderer.SHOW_STRINGS_TYPE = myCbShowStringsType.isSelected();

        final ToStringRenderer toStringRenderer = rendererSettings.getToStringRenderer();
        toStringRenderer.setEnabled(myCbEnableToString.isSelected());
        toStringRenderer.setUseClassFilters(myRbFromList.isSelected());
        toStringRenderer.setClassFilters(myToStringFilterEditor.getFilters());

        PrimitiveRenderer primitiveRenderer = rendererSettings.getPrimitiveRenderer();
        primitiveRenderer.setShowHexValue(myCbHexValue.isSelected());

        rendererSettings.fireRenderersChanged();
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        final ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
        final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

        myCbAutoscroll.setSelected(generalSettings.AUTOSCROLL_TO_NEW_LOCALS);
        myCbHideNullArrayElements.setSelected(generalSettings.HIDE_NULL_ARRAY_ELEMENTS);
        myCbEnableAlternateViews.setSelected(rendererSettings.areAlternateCollectionViewsEnabled());

        ClassRenderer classRenderer = rendererSettings.getClassRenderer();

        myCbShowSyntheticFields.setSelected(classRenderer.SHOW_SYNTHETICS);
        myCbShowValFieldsAsLocalVariables.setSelected(classRenderer.SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES);
        if (!classRenderer.SHOW_SYNTHETICS) {
            myCbShowValFieldsAsLocalVariables.makeUnselectable(false);
        }
        myCbShowDeclaredType.setSelected(classRenderer.SHOW_DECLARED_TYPE);
        myCbShowFQNames.setSelected(classRenderer.SHOW_FQ_TYPE_NAMES);
        myCbShowObjectId.setSelected(classRenderer.SHOW_OBJECT_ID);
        myCbShowStringsType.setSelected(classRenderer.SHOW_STRINGS_TYPE);

        final ToStringRenderer toStringRenderer = rendererSettings.getToStringRenderer();
        final boolean toStringEnabled = toStringRenderer.isEnabled();
        final boolean useClassFilters = toStringRenderer.isUseClassFilters();
        myCbEnableToString.setSelected(toStringEnabled);
        myRbAllThatOverride.setSelected(!useClassFilters);
        myRbFromList.setSelected(useClassFilters);
        myToStringFilterEditor.setFilters(toStringRenderer.getClassFilters());
        myToStringFilterEditor.setEnabled(toStringEnabled && useClassFilters);
        myRbFromList.setEnabled(toStringEnabled);
        myRbAllThatOverride.setEnabled(toStringEnabled);

        PrimitiveRenderer primitiveRenderer = rendererSettings.getPrimitiveRenderer();
        myCbHexValue.setSelected(primitiveRenderer.isShowHexValue());
    }

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        return areGeneralSettingsModified() || areDefaultRenderersModified();
    }

    private boolean areGeneralSettingsModified() {
        ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
        return generalSettings.AUTOSCROLL_TO_NEW_LOCALS != myCbAutoscroll.isSelected() || generalSettings.HIDE_NULL_ARRAY_ELEMENTS != myCbHideNullArrayElements.isSelected();
    }

    private boolean areDefaultRenderersModified() {
        //if (myArrayRendererConfigurable.isModified()) {
        //  return true;
        //}

        final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

        final ClassRenderer classRenderer = rendererSettings.getClassRenderer();
        final boolean isClassRendererModified =
            (classRenderer.SHOW_SYNTHETICS != myCbShowSyntheticFields.isSelected()) ||
                (classRenderer.SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES != myCbShowValFieldsAsLocalVariables.isSelectedWhenSelectable()) ||
                (classRenderer.SHOW_DECLARED_TYPE != myCbShowDeclaredType.isSelected()) ||
                (classRenderer.SHOW_FQ_TYPE_NAMES != myCbShowFQNames.isSelected()) ||
                (classRenderer.SHOW_OBJECT_ID != myCbShowObjectId.isSelected()) ||
                (classRenderer.SHOW_STRINGS_TYPE != myCbShowStringsType.isSelected());

        if (isClassRendererModified) {
            return true;
        }

        final ToStringRenderer toStringRenderer = rendererSettings.getToStringRenderer();
        final boolean isToStringRendererModified = (toStringRenderer.isEnabled() != myCbEnableToString.isSelected()) ||
            (toStringRenderer.isUseClassFilters() != myRbFromList.isSelected()) ||
            (!DebuggerUtilsEx.filterEquals(toStringRenderer.getClassFilters(), myToStringFilterEditor.getFilters()));
        if (isToStringRendererModified) {
            return true;
        }

        if (rendererSettings.areAlternateCollectionViewsEnabled() != myCbEnableAlternateViews.isSelected()) {
            return true;
        }

        PrimitiveRenderer primitiveRenderer = rendererSettings.getPrimitiveRenderer();
        if (primitiveRenderer.isShowHexValue() != myCbHexValue.isSelected()) {
            return true;
        }

        return false;
    }

    @Override
    @Nonnull
    public String getHelpTopic() {
        return "reference.idesettings.debugger.dataviews";
    }

    @Override
    @Nonnull
    public String getId() {
        return getHelpTopic();
    }

    @Override
    public Runnable enableSearch(String option) {
        return null;
    }
}
