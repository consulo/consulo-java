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

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.FullValueEvaluatorProvider;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.execution.filters.ExceptionFilter;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.execution.debug.frame.XFullValueEvaluator;
import consulo.execution.ui.console.Filter;
import consulo.execution.ui.console.HyperlinkInfo;
import consulo.internal.com.sun.jdi.*;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.Collections;

/**
 * @author egor
 */
@ExtensionImpl
class StackTraceElementObjectRenderer extends ToStringBasedRenderer implements FullValueEvaluatorProvider {
  private static final Logger LOG = Logger.getInstance(StackTraceElementObjectRenderer.class);

  @Inject
  public StackTraceElementObjectRenderer(final NodeRendererSettings rendererSettings) {
    super(rendererSettings, "StackTraceElement", null, null);
    setClassName("java.lang.StackTraceElement");
    setEnabled(true);
  }

  @Nullable
  @Override
  public XFullValueEvaluator getFullValueEvaluator(final EvaluationContextImpl evaluationContext,
                                                   final ValueDescriptorImpl valueDescriptor) {
    return new JavaValue.JavaFullValueEvaluator(DebuggerBundle.message("message.node.navigate"), evaluationContext) {
      @Override
      public void evaluate(@Nonnull XFullValueEvaluationCallback callback) {
        Value value = valueDescriptor.getValue();
        ClassType type = ((ClassType)value.type());
        Method toString = type.concreteMethodByName("toString", "()Ljava/lang/String;");
        if (toString != null) {
          try {
            Value res = evaluationContext.getDebugProcess()
                                         .invokeMethod(evaluationContext, (ObjectReference)value, toString, Collections.emptyList());
            if (res instanceof StringReference) {
              callback.evaluated("");
              final String line = ((StringReference)res).value();
              ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                  ExceptionFilter filter = new ExceptionFilter(evaluationContext.getDebugProcess().getSession().getSearchScope());
                  Filter.Result result = filter.applyFilter(line, line.length());
                  if (result != null) {
                    final HyperlinkInfo info = result.getFirstHyperlinkInfo();
                    if (info != null) {
                      valueDescriptor.getProject().getUIAccess().give(() -> info.navigate(valueDescriptor.getProject()));
                    }
                  }
                }
              });
            }
          }
          catch (EvaluateException e) {
            LOG.info("Exception while getting stack info", e);
          }
        }
      }

      @Override
      public boolean isShowValuePopup() {
        return false;
      }
    };
  }
}
