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
package com.intellij.java.debugger.impl.actions;

import consulo.execution.debug.XDebuggerActions;

/**
 * @author Jeka
 */
public interface DebuggerActions extends XDebuggerActions {
    String POP_FRAME = "Debugger.PopFrame";
    String THREADS_PANEL_POPUP = "Debugger.ThreadsPanelPopup";
    String REMOVE_WATCH = "Debugger.RemoveWatch";
    String NEW_WATCH = "Debugger.NewWatch";
    String EDIT_WATCH = "Debugger.EditWatch";
    String COPY_VALUE = "Debugger.CopyValue";
    String SET_VALUE = "Debugger.SetValue";
    String EDIT_FRAME_SOURCE = "Debugger.EditFrameSource";
    String EDIT_NODE_SOURCE = "Debugger.EditNodeSource";
    String REPRESENTATION_LIST = "Debugger.Representation";
    String INSPECT = "Debugger.Inspect";
    String DUMP_THREADS = "DumpThreads";
}
