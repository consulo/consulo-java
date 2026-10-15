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
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.debug.ui.DebuggerUIUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.Component;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.JBPopup;

import javax.swing.*;
import java.awt.*;

/**
 * @author egor
 */
public abstract class CustomPopupFullValueEvaluator<T, C extends Component> extends JavaValue.JavaFullValueEvaluator {
    public CustomPopupFullValueEvaluator(LocalizeValue linkText, EvaluationContextImpl evaluationContext) {
        super(linkText, evaluationContext);
        setShowValuePopup(false);
    }

    protected abstract T getData();

    protected abstract C createComponent(T data);

    protected void afterPopupShow(C component) {
    }

    @Override
    public void evaluate(final XFullValueEvaluationCallback callback) {
        Project project = getEvaluationContext().getProject();
        JFrame frame = WindowManager.getInstance().getFrame(project);

        final T data = getData();
        project.getUIAccess().execute(() -> {
            if (callback.isObsolete()) {
                return;
            }

            final C comp = createComponent(data);
            JBPopup popup = DebuggerUIUtil.createValuePopup(project, (JComponent) TargetAWT.to(comp), null);
            Dimension frameSize = frame.getSize();
            Dimension size = new Dimension(frameSize.width / 2, frameSize.height / 2);
            popup.setSize(size);
            if (comp instanceof Disposable) {
                Disposer.register(popup, (Disposable) comp);
            }
            callback.evaluated("");
            popup.show(new RelativePoint(frame, new Point(size.width / 2, size.height / 2)));

            afterPopupShow(comp);
        });
    }
}
