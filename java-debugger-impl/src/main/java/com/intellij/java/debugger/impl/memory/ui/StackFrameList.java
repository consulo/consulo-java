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
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.ui.console.OpenFileHyperlinkInfo;
import consulo.ide.impl.idea.xdebugger.impl.frame.XDebuggerFramesList;
import consulo.ide.impl.idea.xdebugger.impl.ui.DebuggerUIUtil;
import consulo.navigation.OpenFileDescriptor;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import java.util.List;

class StackFrameList extends XDebuggerFramesList {
  private static final MyOpenFilesState myEditorState = new MyOpenFilesState();

  private final DebugProcessImpl myDebugProcess;

  StackFrameList(DebugProcessImpl debugProcess) {
    super(debugProcess.getProject());
    myDebugProcess = debugProcess;
  }

  void setFrameItems(@Nonnull List<StackFrameItem> items) {
    setFrameItems(items, null);
  }

  void setFrameItems(@Nonnull List<StackFrameItem> items, Runnable onDone) {
    clear();
    if (!items.isEmpty()) {
      myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
        @Override
        protected void action() throws Exception {
          boolean separator = false;
          for (StackFrameItem frameInfo : items) {
            if (frameInfo == null) {
              separator = true;
            } else {
              StackFrameItem.CapturedStackFrame frame = frameInfo.createFrame(myDebugProcess);
              frame.setWithSeparator(separator);
              DebuggerUIUtil.invokeLater(() -> getModel().addElement(frame));
              separator = false;
            }
          }
          if (onDone != null) {
            onDone.run();
          }
        }
      });
    }
  }

  @Override
  protected void onFrameChanged(Object selectedValue) {
    navigateTo(selectedValue, false);
  }

  void navigateToSelectedValue(boolean focusOnEditor) {
    navigateTo(getSelectedValue(), focusOnEditor);
  }

  private void navigateTo(Object frame, boolean focusOnEditor) {
    if (frame instanceof XStackFrame) {
      navigateToFrame((XStackFrame) frame, focusOnEditor);
    }
  }

  private void navigateToFrame(@Nonnull XStackFrame frame, boolean focusOnEditor) {
    XSourcePosition position = frame.getSourcePosition();
    if (position == null) {
      return;
    }

    VirtualFile file = position.getFile();
    int line = position.getLine();

    Project project = myDebugProcess.getProject();

    OpenFileHyperlinkInfo info = new OpenFileHyperlinkInfo(project, file, line);
    OpenFileDescriptor descriptor = info.getDescriptor();
    if (descriptor != null) {
      descriptor.navigate(focusOnEditor);
//      FileEditorManager manager = FileEditorManager.getInstance(project);
//      VirtualFile lastFile = myEditorState.myLastOpenedFile;
//      if (myEditorState.myIsNeedToCloseLastOpenedFile && lastFile != null && manager.isFileOpen(lastFile) && !lastFile.equals(descriptor.getFile())) {
//        manager.closeFile(myEditorState.myLastOpenedFile, false, true);
//      }
//
//      descriptor.setScrollType(ScrollType.CENTER);
//      descriptor.setUseCurrentWindow(true);
//
//      if (lastFile == null || !lastFile.equals(descriptor.getFile())) {
//        myEditorState.myIsNeedToCloseLastOpenedFile = !manager.isFileOpen(descriptor.getFile());
//      }
//
//      descriptor.navigateInEditor(project, focusOnEditor);
//      FileEditor[] editors = manager.getEditors(descriptor.getFile());
//      if (editors.length != 0) {
//        myEditorState.myLastOpenedFile = descriptor.getFile();
//      }
    }
  }

  private static class MyOpenFilesState {
    VirtualFile myLastOpenedFile;
    boolean myIsNeedToCloseLastOpenedFile;
  }
}
