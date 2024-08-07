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

/**
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: Nov 15, 2002
 * Time: 4:12:48 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.java.impl.refactoring.introduceVariable;

import com.intellij.java.language.psi.PsiType;

public interface IntroduceVariableSettings {
  String getEnteredName();

  boolean isReplaceAllOccurrences();

  boolean isDeclareFinal();

  boolean isReplaceLValues();

  PsiType getSelectedType();

  boolean isOK();
}
