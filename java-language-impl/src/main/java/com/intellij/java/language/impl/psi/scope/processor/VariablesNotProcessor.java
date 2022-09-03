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
package com.intellij.java.language.impl.psi.scope.processor;

import java.util.List;

import com.intellij.java.language.psi.PsiVariable;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.collection.SmartList;

public class VariablesNotProcessor extends VariablesProcessor
{
  private final PsiVariable myVariable;

  public VariablesNotProcessor(PsiVariable var, boolean staticSensitive, List<PsiVariable> list){
    super(staticSensitive, list);
    myVariable = var;
  }

  public VariablesNotProcessor(PsiVariable var, boolean staticSensitive){
    this(var, staticSensitive, new SmartList<PsiVariable>());
  }

  @Override
  protected boolean check(PsiVariable var, ResolveState state) {
    String name = var.getName();
    return name != null && name.equals(myVariable.getName()) && !var.equals(myVariable);
  }
}
