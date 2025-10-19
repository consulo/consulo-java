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
package com.intellij.java.debugger.impl.ui.tree.render;

import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.FullValueEvaluatorProvider;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import com.intellij.java.debugger.impl.ui.tree.actions.ForceOnDemandRenderersAction;
import consulo.execution.debug.frame.HeadlessValueEvaluationCallback;
import consulo.execution.debug.frame.XFullValueEvaluator;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.frame.XValuePlace;
import consulo.localize.LocalizeValue;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author egor
 */
public interface OnDemandRenderer extends FullValueEvaluatorProvider {
  @Nullable
  @Override
  default XFullValueEvaluator getFullValueEvaluator(EvaluationContextImpl evaluationContext, ValueDescriptorImpl valueDescriptor) {
    if (isOnDemand(evaluationContext, valueDescriptor) && !isCalculated(valueDescriptor)) {
      return createFullValueEvaluator(getLinkText());
    }
    return null;
  }

  @Nonnull
  LocalizeValue getLinkText();

  default boolean isOnDemand(EvaluationContext evaluationContext, ValueDescriptor valueDescriptor) {
    return isOnDemandForced(evaluationContext);
  }

  default boolean isShowValue(ValueDescriptor valueDescriptor, EvaluationContext evaluationContext) {
    return !isOnDemand(evaluationContext, valueDescriptor) || isCalculated(valueDescriptor);
  }

  static XFullValueEvaluator createFullValueEvaluator(@Nonnull LocalizeValue text) {
    return new XFullValueEvaluator(text) {
      @Override
      public void startEvaluation(@Nonnull XFullValueEvaluationCallback callback) {
        if (callback instanceof HeadlessValueEvaluationCallback) {
          XValueNode node = ((HeadlessValueEvaluationCallback) callback).getNode();
          node.clearFullValueEvaluator();
          setCalculated(((JavaValue) node.getValueContainer()).getDescriptor());
          node.getValueContainer().computePresentation(node, XValuePlace.TREE);
        }
        callback.evaluated("");
      }
    }.setShowValuePopup(false);
  }

  Key<Boolean> ON_DEMAND_CALCULATED = Key.create("ON_DEMAND_CALCULATED");

  static boolean isCalculated(ValueDescriptor descriptor) {
    return ON_DEMAND_CALCULATED.get(descriptor, false);
  }

  static void setCalculated(ValueDescriptor descriptor) {
    ON_DEMAND_CALCULATED.set(descriptor, true);
  }

  static boolean isOnDemandForced(EvaluationContext evaluationContext) {
    return ForceOnDemandRenderersAction.isForcedOnDemand(((DebugProcessImpl) evaluationContext.getDebugProcess()).getXdebugProcess().getSession());
  }
}
