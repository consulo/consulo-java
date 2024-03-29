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
package com.intellij.java.impl.psi.filters.position;

import consulo.language.psi.PsiElement;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.filter.position.PositionElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 25.03.2003
 * Time: 12:11:40
 * To change this template use Options | File Templates.
 */
public class InsideElementFilter extends PositionElementFilter {
  public InsideElementFilter(ElementFilter filter) {
    setFilter(filter);
  }

  public InsideElementFilter() {
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement scope) {
    if (!(element instanceof PsiElement)) return false;
    PsiElement currentChild = getOwnerChild(scope, (PsiElement) element);
    return getFilter().isAcceptable(currentChild, scope);
  }

  public String toString() {
    return "in(" + getFilter().toString() + ")";
  }
}
