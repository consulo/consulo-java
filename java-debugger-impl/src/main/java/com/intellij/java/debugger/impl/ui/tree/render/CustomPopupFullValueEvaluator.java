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
package com.intellij.java.debugger.impl.ui.tree.render;

import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.debug.ui.DebuggerUIUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.popup.JBPopup;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

/**
 * @author egor
 */
public abstract class CustomPopupFullValueEvaluator<T> extends JavaValue.JavaFullValueEvaluator {
    public CustomPopupFullValueEvaluator(@Nonnull LocalizeValue linkText, @Nonnull EvaluationContextImpl evaluationContext) {
        super(linkText, evaluationContext);
        setShowValuePopup(false);
    }

    protected abstract T getData();

    protected abstract JComponent createComponent(T data);

    @Override
    public void evaluate(@Nonnull final XFullValueEvaluationCallback callback) {
        final T data = getData();
        Application.get().invokeLater(() -> {
            if (callback.isObsolete()) {
                return;
            }
            final JComponent comp = createComponent(data);
            Project project = getEvaluationContext().getProject();
            JBPopup popup = DebuggerUIUtil.createValuePopup(project, comp, null);
            JFrame frame = WindowManager.getInstance().getFrame(project);
            Dimension frameSize = frame.getSize();
            Dimension size = new Dimension(frameSize.width / 2, frameSize.height / 2);
            popup.setSize(size);
            if (comp instanceof Disposable) {
                Disposer.register(popup, (Disposable) comp);
            }
            callback.evaluated("");
            popup.show(new RelativePoint(frame, new Point(size.width / 2, size.height / 2)));
        });
    }
}
