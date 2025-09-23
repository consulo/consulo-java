package com.intellij.java.impl.ide.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.IdeActions;

/**
 * @author UNV
 * @since 2025-09-23
 */
@ActionImpl(
    id = "NewJavaSpecialFile",
    children = {
        @ActionRef(type = CreatePackageInfoAction.class),
        @ActionRef(type = CreateModuleInfoAction.class)
    },
    parents = @ActionParentRef(
        value = @ActionRef(id = IdeActions.GROUP_NEW),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "NewDir")
    )
)
public class NewJavaSpecialFileGroup extends DefaultActionGroup implements DumbAware {
    public NewJavaSpecialFileGroup() {
        super(LocalizeValue.empty(), false);
    }
}
