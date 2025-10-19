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
package com.intellij.java.debugger.image.impl;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.DebuggerUtilsImpl;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.FullValueEvaluatorProvider;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.DescriptorLabelListener;
import com.intellij.java.debugger.impl.ui.tree.render.ToStringBasedRenderer;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.debug.frame.XFullValueEvaluator;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.image.Image;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

/**
 * Created by Egor on 04.10.2014.
 */
@ExtensionImpl
class IconObjectRenderer extends ToStringBasedRenderer implements FullValueEvaluatorProvider {
  @Inject
  public IconObjectRenderer(final NodeRendererSettings rendererSettings) {
    super(rendererSettings, "Icon", null, null);
    setClassName("javax.swing.Icon");
    setEnabled(true);
  }

  @Override
  public Image calcValueIcon(final ValueDescriptor descriptor,
                             final EvaluationContext evaluationContext,
                             final DescriptorLabelListener listener) throws EvaluateException {
    EvaluationContextImpl evalContext = ((EvaluationContextImpl)evaluationContext);
    DebugProcessImpl debugProcess = evalContext.getDebugProcess();

    if (DebuggerUtilsImpl.isRemote(debugProcess)) {
      return null;
    }

    debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(evalContext.getSuspendContext()) {
      @Override
      public void contextAction(SuspendContextImpl context) throws Exception {
        String getterName = JBUI.sysScale() > 1 ? "iconToBytesPreviewRetina" : "iconToBytesPreviewNormal";
        descriptor.setValueIcon(ImageObjectRenderer.getIcon(evaluationContext, descriptor.getValue(), getterName));
        listener.labelChanged();
      }
    });
    return null;
  }

  @Nullable
  @Override
  public XFullValueEvaluator getFullValueEvaluator(final EvaluationContextImpl evaluationContext,
                                                   final ValueDescriptorImpl valueDescriptor) {
    return new ImageObjectRenderer.IconPopupEvaluator(JavaDebuggerLocalize.messageNodeShowIcon(), evaluationContext) {
      @Override
      protected Image getData() {
        return ImageObjectRenderer.getIcon(getEvaluationContext(), valueDescriptor.getValue(), "iconToBytes");
      }
    };
  }
}
