package com.intellij.java.debugger.impl.ui.breakpoints;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicImpl;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.event.XTopicBreakpointListener;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2024-08-17
 */
@TopicImpl(ComponentScope.PROJECT)
public class BreakpointTopicListenerForManager implements XTopicBreakpointListener {
    @Override
    public void breakpointAdded(@Nonnull XBreakpoint<?> xBreakpoint) {
        Breakpoint breakpoint = BreakpointManager.getJavaBreakpoint(xBreakpoint);
        if (breakpoint != null) {
            BreakpointManager.addBreakpoint(breakpoint);
        }
    }

    @Override
    public void breakpointChanged(@Nonnull XBreakpoint xBreakpoint) {
        Breakpoint breakpoint = BreakpointManager.getJavaBreakpoint(xBreakpoint);
        if (breakpoint != null) {
            BreakpointManager.fireBreakpointChanged(breakpoint);
        }
    }
}
