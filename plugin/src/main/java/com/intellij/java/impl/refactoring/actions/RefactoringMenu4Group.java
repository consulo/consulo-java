/*
 * Copyright 2013-2025 consulo.io
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
package com.intellij.java.impl.refactoring.actions;

import com.intellij.java.impl.refactoring.wrapreturnvalue.WrapReturnValueAction;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.IdeActions;

/**
 * @author UNV
 * @since 2025-10-29
 */
@ActionImpl(
    id = "RefactoringMenu4",
    children = {
        @ActionRef(type = TurnRefsToSuperAction.class),
        @ActionRef(type = InheritanceToDelegationAction.class),
        @ActionRef(type = RemoveMiddlemanAction.class),
        @ActionRef(type = WrapReturnValueAction.class),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = AnonymousToInnerAction.class),
        @ActionRef(type = EncapsulateFieldsAction.class),
        @ActionRef(type = TempWithQueryAction.class),
        @ActionRef(type = ReplaceConstructorWithFactoryAction.class),
        @ActionRef(type = ReplaceConstructorWithBuilderAction.class),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = TypeCookAction.class),
        @ActionRef(type = MigrateAction.class),
        @ActionRef(type = AnSeparator.class)
    },
    parents = @ActionParentRef(
        value = @ActionRef(id = IdeActions.GROUP_REFACTOR),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "MemberPushDown")
    )
)
public class RefactoringMenu4Group extends DefaultActionGroup implements DumbAware {
    public RefactoringMenu4Group() {
        super(LocalizeValue.empty(), false);
    }
}
