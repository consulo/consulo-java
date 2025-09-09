package com.intellij.java.debugger.impl.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.platform.base.localize.ActionLocalize;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.IdeActions;

/**
 * @author UNV
 * @since 2025-09-06
 */
@ActionImpl(
    id = "ToggleBreakpointAction",
    children = {
        @ActionRef(type = ToggleMethodBreakpointAction.class),
        @ActionRef(type = ToggleFieldBreakpointAction.class)
    },
    parents = @ActionParentRef(
        value = @ActionRef(id = "DebugMainMenu"),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)
    )
)
public class ToggleBreakpointGroup extends DefaultActionGroup implements DumbAware {
    public ToggleBreakpointGroup() {
        super(ActionLocalize.groupDebugmainmenuText(), false);
    }
}
