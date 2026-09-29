/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.debugger.impl.classFilter;

import com.intellij.java.debugger.ui.classFilter.ClassFilter;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.ComponentItemRender;
import consulo.ui.PseudoComponent;
import consulo.ui.Table;
import consulo.ui.TableItemEditor;
import consulo.ui.TextAttribute;
import consulo.ui.TextBox;
import consulo.ui.ValueComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.localize.UILocalize;
import consulo.ui.ex.toolbar.AddAction;
import consulo.ui.ex.toolbar.DownMoveAction;
import consulo.ui.ex.toolbar.ToolbarDecoratorBuilder;
import consulo.ui.ex.toolbar.ToolbarDecoratorBuilderFactory;
import consulo.ui.ex.toolbar.UpMoveAction;
import consulo.ui.image.Image;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UnifiedClassFilterEditor implements PseudoComponent {
    private static final class FilterRow {
        private final ClassFilter myFilter;

        private FilterRow(ClassFilter filter) {
            myFilter = filter;
        }
    }

    protected final Project myProject;
    private final com.intellij.java.language.util.@Nullable ClassFilter myChooserFilter;
    private final MutableFlatDataModel<FilterRow> myModel;
    private final Table<FilterRow> myTable;
    private @Nullable Component myComponent;

    @RequiredUIAccess
    public UnifiedClassFilterEditor(Project project) {
        this(project, null);
    }

    @RequiredUIAccess
    public UnifiedClassFilterEditor(Project project, com.intellij.java.language.util.@Nullable ClassFilter classFilter) {
        myProject = project;
        myChooserFilter = classFilter;
        myModel = FlatDataModel.of(List.of());

        myTable = Table.create(myModel);
        myTable.setShowHeader(false);
        myTable.addColumn(LocalizeValue.empty(), row -> row.myFilter.isEnabled())
            .setWidth(40)
            .setRender(ComponentItemRender.reusable(
                () -> CheckBox.create(LocalizeValue.empty()),
                (checkBox, item) -> checkBox.setValue(Boolean.TRUE.equals(item.getValue()))
            ))
            .setEditor(new TableItemEditor<>() {
                @RequiredUIAccess
                @Override
                public ValueComponent<Boolean> createComponent(FilterRow row) {
                    return CheckBox.create(LocalizeValue.empty(), row.myFilter.isEnabled());
                }

                @RequiredUIAccess
                @Override
                public void commit(FilterRow row, @Nullable Boolean value) {
                    row.myFilter.setEnabled(Boolean.TRUE.equals(value));
                    myModel.update(row);
                }
            });
        myTable.addColumn(LocalizeValue.empty(), row -> row.myFilter.getPattern())
            .setRender((presentation, value, row) ->
                presentation.append(StringUtil.notNullize(value.getValue()), row.myFilter.isEnabled() ? TextAttribute.REGULAR : TextAttribute.GRAYED))
            .setEditor(new TableItemEditor<>() {
                @RequiredUIAccess
                @Override
                public ValueComponent<String> createComponent(FilterRow row) {
                    return TextBox.create(row.myFilter.getPattern());
                }

                @RequiredUIAccess
                @Override
                public void commit(FilterRow row, @Nullable String value) {
                    row.myFilter.setPattern(StringUtil.notNullize(value).trim());
                    myModel.update(row);
                }
            });
    }

    @Override
    @RequiredUIAccess
    public Component getComponent() {
        Component component = myComponent;
        if (component == null) {
            component = createComponent();
            myComponent = component;
        }
        return component;
    }

    @RequiredUIAccess
    private Component createComponent() {
        ToolbarDecoratorBuilder<FilterRow> builder = ToolbarDecoratorBuilderFactory.getInstance()
            .create(myTable)
            .addOrReplaceAction(new AddAction<>() {
                @Override
                @RequiredUIAccess
                protected void doAdd(AnActionEvent e) {
                    addRow(createFilter(""));
                }
            })
            .disableAction(UpMoveAction.class)
            .disableAction(DownMoveAction.class);

        if (!myProject.isDefault() && !myProject.getApplication().isUnifiedApplication()) {
            builder.addExtraAction(DumbAwareAction.create(getAddButtonText(), LocalizeValue.empty(), getAddButtonIcon(), e -> addClassFilter()));
            if (addPatternButtonVisible()) {
                builder.addExtraAction(DumbAwareAction.create(
                    getAddPatternButtonText(),
                    LocalizeValue.empty(),
                    getAddPatternButtonIcon(),
                    e -> addPatternFilter()
                ));
            }
        }
        return builder.build();
    }

    protected LocalizeValue getAddButtonText() {
        return UILocalize.buttonAddClass();
    }

    protected Image getAddButtonIcon() {
        return PlatformIconGroup.nodesClass();
    }

    protected LocalizeValue getAddPatternButtonText() {
        return UILocalize.buttonAddPattern();
    }

    protected Image getAddPatternButtonIcon() {
        return PlatformIconGroup.actionsRegex();
    }

    protected boolean addPatternButtonVisible() {
        return false;
    }

    @RequiredUIAccess
    protected void addPatternFilter() {
    }

    @RequiredUIAccess
    protected void addClassFilter() {
        String pattern = ClassFilterEditor.chooseClassPattern(myProject, myChooserFilter);
        if (pattern != null) {
            addRow(createFilter(pattern));
        }
    }

    protected ClassFilter createFilter(String pattern) {
        return new ClassFilter(pattern);
    }

    @RequiredUIAccess
    public void addPattern(String pattern) {
        addRow(createFilter(pattern));
    }

    @RequiredUIAccess
    private void addRow(ClassFilter filter) {
        FilterRow row = new FilterRow(filter);
        myModel.add(row);
        myTable.select(row);
        myTable.scrollTo(row);
    }

    @RequiredUIAccess
    public void setFilters(ClassFilter @Nullable [] filters) {
        List<FilterRow> rows = new ArrayList<>();
        if (filters != null) {
            for (ClassFilter filter : filters) {
                rows.add(new FilterRow(filter.clone()));
            }
        }
        myModel.replaceAll(rows);
    }

    public ClassFilter[] getFilters() {
        List<ClassFilter> filters = new ArrayList<>();
        for (FilterRow row : myModel) {
            String pattern = row.myFilter.getPattern();
            if (!StringUtil.isEmpty(pattern)) {
                filters.add(row.myFilter.clone());
            }
        }
        return filters.toArray(ClassFilter.EMPTY_ARRAY);
    }
}
