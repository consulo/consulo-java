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
package com.intellij.java.impl.codeInsight.generation.actions;

import com.intellij.java.impl.generate.GenerateToStringAction;
import com.intellij.java.impl.testIntegration.GenerateDataMethodAction;
import com.intellij.java.impl.testIntegration.GenerateSetUpMethodAction;
import com.intellij.java.impl.testIntegration.GenerateTearDownMethodAction;
import com.intellij.java.impl.testIntegration.GenerateTestMethodAction;
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
 * @since 2025-09-23
 */
@ActionImpl(
    id = "JavaGenerateGroup1",
    children = {
        @ActionRef(type = GenerateTestMethodAction.class),
        @ActionRef(type = GenerateSetUpMethodAction.class),
        @ActionRef(type = GenerateTearDownMethodAction.class),
        @ActionRef(type = GenerateDataMethodAction.class),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = GenerateConstructorAction.class),
        @ActionRef(type = GenerateGetterAction.class),
        @ActionRef(type = GenerateSetterAction.class),
        @ActionRef(type = GenerateGetterAndSetterAction.class),
        @ActionRef(type = GenerateEqualsAction.class),
        @ActionRef(type = GenerateToStringAction.class),
        @ActionRef(type = GenerateCreateUIAction.class)
    },
    parents = @ActionParentRef(value = @ActionRef(id = IdeActions.GROUP_GENERATE), anchor = ActionRefAnchor.FIRST)
)
public class JavaGenerateGroup1 extends DefaultActionGroup implements DumbAware {
    public JavaGenerateGroup1() {
        super(LocalizeValue.empty(), true);
    }
}
