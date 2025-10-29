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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.ui.tree.render.ArrayRenderer;
import consulo.annotation.component.ActionImpl;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.localize.XDebuggerLocalize;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.AsyncResult;
import jakarta.annotation.Nonnull;

@ActionImpl(id = "Debugger.AdjustArrayRange")
public class AdjustArrayRangeAction extends ArrayAction {
    public AdjustArrayRangeAction() {
        super(XDebuggerLocalize.actionAdjustArrayRangeText());
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    protected AsyncResult<ArrayRenderer> createNewRenderer(
        XValueNode node,
        ArrayRenderer original,
        @Nonnull DebuggerContextImpl debuggerContext,
        LocalizeValue title
    ) {
        ArrayRenderer clonedRenderer = original.clone();
        clonedRenderer.setForced(true);
        AsyncResult<ArrayRenderer> result = AsyncResult.undefined();
        AsyncResult<Void> showResult = ShowSettingsUtil.getInstance()
            .editConfigurable(debuggerContext.getProject(), new NamedArrayConfigurable(title, clonedRenderer));
        showResult.doWhenDone(() -> result.setDone(clonedRenderer));
        showResult.doWhenRejected((Runnable)result::setRejected);
        return result;
    }
}
