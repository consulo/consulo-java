/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.ide.hierarchy.method;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.ide.localize.IdeLocalize;
import consulo.platform.base.localize.ActionLocalize;
import consulo.ui.ex.action.Presentation;

@ActionImpl(id = "MethodHierarchy.OverrideMethodAction", shortcutFrom = @ActionRef(id = "OverrideMethods"))
public final class OverrideMethodAction extends OverrideImplementMethodAction {
    public OverrideMethodAction() {
        super(
            ActionLocalize.actionMethodhierarchyOverridemethodactionText(),
            ActionLocalize.actionMethodhierarchyOverridemethodactionDescription()
        );
    }

    @Override
    protected final void update(Presentation presentation, int toImplement, int toOverride) {
        boolean enabled = toOverride > 0;
        presentation.setEnabledAndVisible(enabled);
        if (enabled) {
            presentation.setTextValue(toOverride == 1 ? IdeLocalize.actionOverrideMethod() : IdeLocalize.actionOverrideMethods());
        }
    }
}
