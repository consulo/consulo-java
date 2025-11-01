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
package com.intellij.java.impl.codeInsight.generation.actions;

import com.intellij.java.impl.codeInsight.generation.GenerateGetterAndSetterHandler;
import consulo.annotation.component.ActionImpl;
import consulo.java.localize.JavaLocalize;

/**
 * Action group which contains Generate... actions
 * Available in the Java code editor context only
 *
 * @author Alexey Kudravtsev
 */
@ActionImpl(id = "GenerateGetterAndSetter")
public class GenerateGetterAndSetterAction extends GenerateGetterSetterBaseAction {
    public GenerateGetterAndSetterAction() {
        super(new GenerateGetterAndSetterHandler(), JavaLocalize.actionGenerateGetterAndSetterText());
    }
}