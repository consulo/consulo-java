package com.intellij.java.debugger.impl.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.platform.base.localize.ActionLocalize;
import consulo.ui.ex.action.DefaultActionGroup;

/**
 * @author UNV
 * @since 2025-09-06
 */
@ActionImpl(
    id = "JavaDebugMainMenu",
    children = {
        @ActionRef(type = ExportThreadsAction.class),
        @ActionRef(type = ThreadDumpAction.class)
    },
    parents = @ActionParentRef(value = @ActionRef(id = "DebugMainMenu"))
)
public class JavaDebugMainMenuGroup extends DefaultActionGroup implements DumbAware {
    public JavaDebugMainMenuGroup() {
        super(ActionLocalize.groupDebugmainmenuText(), false);
    }
}
