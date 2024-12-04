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
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.HelpID;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaMethodBreakpointProperties;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XLineBreakpoint;
import consulo.execution.debug.breakpoint.ui.XBreakpointCustomPropertiesPanel;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 26, 2005
 */
@ExtensionImpl
public class JavaMethodBreakpointType extends JavaLineBreakpointTypeBase<JavaMethodBreakpointProperties> implements JavaBreakpointType {
    @Nonnull
    public static JavaMethodBreakpointType getInstance() {
        return EXTENSION_POINT_NAME.findExtension(JavaMethodBreakpointType.class);
    }

    public JavaMethodBreakpointType() {
        super("java-method", DebuggerBundle.message("method.breakpoints.tab.title"));
    }

    @Nonnull
    @Override
    public Image getEnabledIcon() {
        return ExecutionDebugIconGroup.breakpointBreakpointmethod();
    }

    @Nonnull
    @Override
    public Image getDisabledIcon() {
        return ExecutionDebugIconGroup.breakpointBreakpointmethoddisabled();
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
    public String getShortText(XLineBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
        return getText(breakpoint);
    }

    static String getText(XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
        final StringBuilder buffer = new StringBuilder();
        //if(isValid()) {
        final String className = breakpoint.getProperties().myClassPattern;
        final boolean classNameExists = className != null && className.length() > 0;
        if (classNameExists) {
            buffer.append(className);
        }
        if (breakpoint.getProperties().myMethodName != null) {
            if (classNameExists) {
                buffer.append(".");
            }
            buffer.append(breakpoint.getProperties().myMethodName);
        }
        //}
        //else {
        //  buffer.append(DebuggerBundle.message("status.breakpoint.invalid"));
        //}
        return buffer.toString();
    }

    @Nullable
    @Override
    public XBreakpointCustomPropertiesPanel createCustomPropertiesPanel(@Nonnull Project project) {
        return new MethodBreakpointPropertiesPanel();
    }

    @Nullable
    @Override
    public JavaMethodBreakpointProperties createProperties() {
        return new JavaMethodBreakpointProperties();
    }

    @Nullable
    @Override
    public JavaMethodBreakpointProperties createBreakpointProperties(@Nonnull VirtualFile file, int line) {
        return new JavaMethodBreakpointProperties();
    }

    @Nonnull
    @Override
    public Breakpoint createJavaBreakpoint(Project project, XBreakpoint breakpoint) {
        return new MethodBreakpoint(project, breakpoint);
    }

    @Override
    public boolean canBeHitInOtherPlaces() {
        return true;
    }
}
