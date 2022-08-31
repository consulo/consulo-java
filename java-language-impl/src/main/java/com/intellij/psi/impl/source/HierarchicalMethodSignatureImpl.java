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
package com.intellij.psi.impl.source;

import com.intellij.java.language.psi.HierarchicalMethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.SmartList;
import javax.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class HierarchicalMethodSignatureImpl extends HierarchicalMethodSignature {
  private List<HierarchicalMethodSignature> mySupers;

  public HierarchicalMethodSignatureImpl(@Nonnull MethodSignatureBackedByPsiMethod signature) {
    super(signature);
  }

  public void addSuperSignature(@Nonnull HierarchicalMethodSignature superSignatureHierarchical) {
    if (mySupers == null) mySupers = new SmartList<HierarchicalMethodSignature>();
    mySupers.add(superSignatureHierarchical);
  }

  @Override
  @Nonnull
  public List<HierarchicalMethodSignature> getSuperSignatures() {
    return mySupers == null ? Collections.<HierarchicalMethodSignature>emptyList() : mySupers;
  }
}
