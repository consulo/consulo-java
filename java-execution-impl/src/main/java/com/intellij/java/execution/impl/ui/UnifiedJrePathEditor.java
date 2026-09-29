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

import com.intellij.java.language.projectRoots.JavaSdkType;
import consulo.disposer.Disposable;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.module.ui.BundleBox;
import consulo.module.ui.BundleBoxBuilder;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.Component;
import consulo.ui.PseudoComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public class UnifiedJrePathEditor implements PseudoComponent {
    private final BundleBox myBundleBox;

    @RequiredUIAccess
    public UnifiedJrePathEditor(Disposable uiDisposable) {
        myBundleBox = BundleBoxBuilder.create(uiDisposable)
            .withSdkTypeFilter(id -> id instanceof JavaSdkType)
            .withNoneItem(JavaExecutionLocalize.runConfigurationJreAutoSelect(), PlatformIconGroup.actionsFind())
            .build();
    }

    @Override
    @RequiredUIAccess
    public Component getComponent() {
        return myBundleBox.getComponent();
    }

    public @Nullable String getJrePathOrName() {
        return myBundleBox.getSelectedBundleName();
    }

    public boolean isAlternativeJreSelected() {
        return !(myBundleBox.getComponent().getValue() instanceof BundleBox.NullBundleBoxItem);
    }

    @RequiredUIAccess
    public void setByName(@Nullable String name) {
        myBundleBox.setSelectedBundle(StringUtil.nullize(name, true));
    }

    public Disposable addValueListener(Runnable listener) {
        return myBundleBox.getComponent().addValueListener(event -> listener.run());
    }
}
