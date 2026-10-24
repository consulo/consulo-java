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

import com.intellij.java.execution.ShortenCommandLine;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.PseudoComponent;
import consulo.ui.TextAttribute;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UnifiedShortenCommandLineModeCombo implements PseudoComponent {
    private record Item(@Nullable ShortenCommandLine mode, ShortenCommandLine shownMode) {
    }

    private final Project myProject;
    private final MutableFlatDataModel<Item> myModel;
    private final ComboBox<Item> myComboBox;

    @RequiredUIAccess
    public UnifiedShortenCommandLineModeCombo(Project project) {
        myProject = project;
        myModel = FlatDataModel.of(List.of());
        myComboBox = ComboBox.create(myModel);
        myComboBox.setRenderer((presentation, renderItem) -> {
            Item item = renderItem.getValue();
            if (item == null) {
                return;
            }

            ShortenCommandLine shownMode = item.shownMode();
            if (item.mode() == null) {
                presentation.append(JavaExecutionLocalize.shortenCommandLineUserLocalDefault(shownMode.getPresentableName()));
            }
            else {
                presentation.append(shownMode.getPresentableName());
            }
            presentation.append(" - " + shownMode.getDescription(), TextAttribute.GRAYED);
        });

        refresh(null);
    }

    @Override
    @RequiredUIAccess
    public Component getComponent() {
        return myComboBox;
    }

    public @Nullable ShortenCommandLine getSelectedItem() {
        Item item = myComboBox.getValue();
        return item == null ? null : item.mode();
    }

    @RequiredUIAccess
    public void setSelectedItem(@Nullable ShortenCommandLine mode) {
        for (Item item : myModel) {
            if (item.mode() == mode) {
                myComboBox.setValue(item, false);
                return;
            }
        }
        myComboBox.selectFirst();
    }

    /**
     * Lists the modes the JDK at {@code jdkRoot} supports, keeping the selection.
     */
    @RequiredUIAccess
    public void refresh(@Nullable String jdkRoot) {
        ShortenCommandLine preselection = getSelectedItem();

        List<Item> items = new ArrayList<>();
        items.add(new Item(null, ShortenCommandLine.getDefaultMethod(myProject, jdkRoot)));
        for (ShortenCommandLine mode : getApplicableModes(jdkRoot)) {
            items.add(new Item(mode, mode));
        }

        myModel.replaceAll(items);
        setSelectedItem(preselection);
    }

    public static List<ShortenCommandLine> getApplicableModes(@Nullable String jdkRoot) {
        List<ShortenCommandLine> modes = new ArrayList<>();
        for (ShortenCommandLine mode : ShortenCommandLine.values()) {
            if (mode.isApplicable(jdkRoot)) {
                modes.add(mode);
            }
        }
        return modes;
    }

    public static @Nullable String getJdkRoot(boolean alternativeJreSelected, @Nullable String jrePathOrName, @Nullable Module module) {
        if (!alternativeJreSelected && module != null) {
            Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
            return sdk != null ? sdk.getHomePath() : null;
        }
        if (jrePathOrName != null) {
            Sdk configuredJdk = SdkTable.getInstance().findSdk(jrePathOrName);
            if (configuredJdk != null) {
                return configuredJdk.getHomePath();
            }
            else {
                return jrePathOrName;
            }
        }
        return null;
    }
}
