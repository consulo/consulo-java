/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.HelpID;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaMethodBreakpointProperties;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.ui.XBreakpointCustomPropertiesPanel;
import consulo.project.Project;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;

/**
 * @author Egor
 */
@ExtensionImpl
public class JavaWildcardMethodBreakpointType extends JavaBreakpointTypeBase<JavaMethodBreakpointProperties> implements JavaBreakpointType {
  public JavaWildcardMethodBreakpointType() {
    super("java-wildcard-method", DebuggerBundle.message("method.breakpoints.tab.title"));
  }

  @Nonnull
  @Override
  public Image getEnabledIcon() {
    return AllIcons.Debugger.Db_method_breakpoint;
  }

  @Nonnull
  @Override
  public Image getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_method_breakpoint;
  }

  //@Override
  protected String getHelpID() {
    return HelpID.METHOD_BREAKPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return DebuggerBundle.message("method.breakpoints.tab.title");
  }

  @Override
  public String getDisplayText(XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    return JavaMethodBreakpointType.getText(breakpoint);
  }

  @Nullable
  @Override
  public XBreakpointCustomPropertiesPanel<XBreakpoint<JavaMethodBreakpointProperties>> createCustomPropertiesPanel() {
    return new MethodBreakpointPropertiesPanel();
  }

  //@Override
  //public Key<MethodBreakpoint> getBreakpointCategory() {
  //  return MethodBreakpoint.CATEGORY;
  //}

  @Nullable
  @Override
  public JavaMethodBreakpointProperties createProperties() {
    return new JavaMethodBreakpointProperties();
  }

  @Nullable
  @Override
  public XBreakpoint<JavaMethodBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent) {
    final AddWildcardBreakpointDialog dialog = new AddWildcardBreakpointDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return null;
    }
    return ApplicationManager.getApplication().runWriteAction(new Computable<XBreakpoint<JavaMethodBreakpointProperties>>() {
      @Override
      public XBreakpoint<JavaMethodBreakpointProperties> compute() {
        return XDebuggerManager.getInstance(project).getBreakpointManager().addBreakpoint(JavaWildcardMethodBreakpointType.this,
                                                                                          new JavaMethodBreakpointProperties(dialog.getClassPattern(),
                                                                                                                             dialog.getMethodName()));
      }
    });
  }

  @Override
  public Breakpoint createJavaBreakpoint(Project project, XBreakpoint breakpoint) {
    return new WildcardMethodBreakpoint(project, breakpoint);
  }
}
