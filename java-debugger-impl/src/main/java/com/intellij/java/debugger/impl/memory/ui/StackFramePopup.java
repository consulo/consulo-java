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
package com.intellij.java.debugger.impl.memory.ui;

import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import consulo.ide.impl.idea.xdebugger.impl.ui.DebuggerUIUtil;
import consulo.ui.ex.awt.popup.AWTPopupFactory;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import jakarta.annotation.Nonnull;

import java.util.List;

public class StackFramePopup {
  public static void show(@Nonnull List<StackFrameItem> stack, DebugProcessImpl debugProcess) {
    StackFrameList list = new StackFrameList(debugProcess);
    list.setFrameItems(stack, () -> DebuggerUIUtil.invokeLater(() ->
    {
      JBPopup popup = ((AWTPopupFactory) JBPopupFactory.getInstance()).createListPopupBuilder(list)
          .setItemChoosenCallback(() -> list.navigateToSelectedValue(true))
          .setTitle("Select stack frame").setAutoSelectIfEmpty(true).setResizable(false)
          .createPopup();

      list.setSelectedIndex(1);
      popup.showInFocusCenter();
    }));
  }
}
