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
package com.intellij.java.execution.impl.ui;

import consulo.disposer.Disposable;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.PseudoComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class UnifiedConfigurationModuleSelector extends BaseConfigurationModuleSelector implements PseudoComponent {
    private record ModuleItem(@Nullable Module module) {
        private static final ModuleItem NONE = new ModuleItem(null);
    }

    private final MutableFlatDataModel<ModuleItem> myModel;
    private final ComboBox<ModuleItem> myComboBox;

    private volatile @Nullable Module mySelectedModule;

    @RequiredUIAccess
    public UnifiedConfigurationModuleSelector(Project project, LocalizeValue emptySelectionText) {
        super(project);
        myModel = FlatDataModel.of(List.of(ModuleItem.NONE));
        myComboBox = ComboBox.create(myModel);
        myComboBox.setRender((presentation, item) -> {
            ModuleItem moduleItem = item.getValue();
            Module module = moduleItem == null ? null : moduleItem.module();
            if (module == null) {
                presentation.append(emptySelectionText);
            }
            else {
                presentation.withIcon(PlatformIconGroup.nodesModule());
                presentation.append(module.getName());
            }
        });
        myComboBox.setValue(ModuleItem.NONE, false);
        myComboBox.addValueListener(event -> {
            ModuleItem item = event.getValue();
            mySelectedModule = item == null ? null : item.module();
        });
    }

    @Override
    @RequiredUIAccess
    public Component getComponent() {
        return myComboBox;
    }

    public Disposable addValueListener(Consumer<@Nullable Module> listener) {
        return myComboBox.addValueListener(event -> {
            ModuleItem item = event.getValue();
            listener.accept(item == null ? null : item.module());
        });
    }

    @Override
    public void applyTo(ModuleBasedConfiguration configurationModule) {
        configurationModule.setModule(getModule());
    }

    @Override
    @RequiredUIAccess
    public void reset(ModuleBasedConfiguration configuration) {
        reset();
        setSelectedModule(configuration.getConfigurationModule().getModule());
    }

    @Override
    @RequiredUIAccess
    protected void setModules(Collection<Module> modules) {
        List<Module> sorted = new ArrayList<>(modules);
        sorted.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));

        List<ModuleItem> items = new ArrayList<>(sorted.size() + 1);
        items.add(ModuleItem.NONE);
        for (Module module : sorted) {
            items.add(new ModuleItem(module));
        }

        Module selected = mySelectedModule;
        myModel.replaceAll(items);
        setSelectedModule(selected);
    }

    @Override
    public @Nullable Module getModule() {
        return mySelectedModule;
    }

    @RequiredUIAccess
    public void setSelectedModule(@Nullable Module module) {
        ModuleItem item = module == null ? ModuleItem.NONE : new ModuleItem(module);
        if (myModel.indexOf(item) < 0) {
            myModel.add(item);
        }
        mySelectedModule = module;
        myComboBox.setValue(item);
    }
}
