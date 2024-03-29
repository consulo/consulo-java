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

import jakarta.annotation.Nonnull;
import consulo.util.dataholder.Key;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.resolve.BaseScopeProcessor;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.psi.scope.JavaScopeProcessorEvent;
import consulo.util.collection.SmartList;

public abstract class VariablesProcessor extends BaseScopeProcessor implements ElementClassHint {
  private boolean myStaticScopeFlag = false;
  private final boolean myStaticSensitiveFlag;
  private final List<PsiVariable> myResultList;

  /** Collecting _all_ variables in scope */
  public VariablesProcessor(boolean staticSensitive){
    this(staticSensitive, new SmartList<PsiVariable>());
  }

  /** Collecting _all_ variables in scope */
  public VariablesProcessor(boolean staticSensitive, List<PsiVariable> list){
    myStaticSensitiveFlag = staticSensitive;
    myResultList = list;
  }

  protected abstract boolean check(PsiVariable var, ResolveState state);

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    return kind == DeclarationKind.VARIABLE || kind == DeclarationKind.FIELD || kind == DeclarationKind.ENUM_CONST;
  }

  /** Always return true since we wanna get all vars in scope */
  @Override
  public boolean execute(@Nonnull PsiElement pe, ResolveState state){
    if(pe instanceof PsiVariable){
      final PsiVariable pvar = (PsiVariable)pe;
      if(!myStaticSensitiveFlag || !myStaticScopeFlag || pvar.hasModifierProperty(PsiModifier.STATIC)){
        if(check(pvar, state)){
          myResultList.add(pvar);
        }
      }
    }
    return true;
  }

  @Override
  public final void handleEvent(Event event, Object associated){
    if(event == JavaScopeProcessorEvent.START_STATIC)
      myStaticScopeFlag = true;
  }

  public int size(){
    return myResultList.size();
  }

  public PsiVariable getResult(int i){
    return myResultList.get(i);
  }

  @Override
  public <T> T getHint(@Nonnull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }

    return super.getHint(hintKey);
  }
}
