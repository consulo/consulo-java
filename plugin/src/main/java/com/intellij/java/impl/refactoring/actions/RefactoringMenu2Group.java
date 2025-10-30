package com.intellij.java.impl.refactoring.actions;

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
    id = "RefactoringMenu2",
    children = {
        @ActionRef(type = MethodDuplicatesAction.class),
        @ActionRef(type = InvertBooleanAction.class)
    },
    parents = @ActionParentRef(
        value = @ActionRef(id = IdeActions.GROUP_REFACTOR),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "Inline")
    )
)
public class RefactoringMenu2Group extends DefaultActionGroup implements DumbAware {
    public RefactoringMenu2Group() {
        super(LocalizeValue.empty(), false);
    }
}
