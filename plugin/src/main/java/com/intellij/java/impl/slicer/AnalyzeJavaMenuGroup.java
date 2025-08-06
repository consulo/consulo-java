package com.intellij.java.impl.slicer;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.DefaultActionGroup;

/**
 * @author UNV
 * @since 2025-08-06
 */
@ActionImpl(
    id = "AnalyzeJavaMenu",
    children = {
        @ActionRef(type = SliceBackwardAction.class),
        @ActionRef(type = SliceForwardAction.class)
    },
    parents = @ActionParentRef(value = @ActionRef(id = "AnalyzeMenu"), anchor = ActionRefAnchor.LAST)
)
public class AnalyzeJavaMenuGroup extends DefaultActionGroup implements DumbAware {
    public AnalyzeJavaMenuGroup() {
        super(LocalizeValue.empty(), false);
    }
}
