package com.intellij.java.impl.refactoring.actions;

import com.intellij.java.impl.refactoring.typeMigration.actions.ChangeTypeSignatureAction;
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
    id = "RefactoringMenu1",
    children = {
        @ActionRef(type = ChangeTypeSignatureAction.class),
        @ActionRef(type = MakeStaticAction.class),
        @ActionRef(type = ConvertToInstanceMethodAction.class)
    },
    parents = @ActionParentRef(
        value = @ActionRef(id = IdeActions.GROUP_REFACTOR),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "ChangeSignature")
    )
)
public class RefactoringMenu1Group extends DefaultActionGroup implements DumbAware {
    public RefactoringMenu1Group() {
        super(LocalizeValue.empty(), false);
    }
}
