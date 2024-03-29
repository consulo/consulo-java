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

import com.intellij.java.language.psi.javadoc.PsiDocToken;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.filter.ElementFilter;
import consulo.util.lang.reflect.ReflectionUtil;
import consulo.xml.psi.xml.XmlToken;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.03.2003
 * Time: 12:10:08
 * To change this template use Options | File Templates.
 */
public class TokenTypeFilter implements ElementFilter{
  private IElementType myType = null;

  public TokenTypeFilter(){}

  public TokenTypeFilter(IElementType type){
    myType = type;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return ReflectionUtil.isAssignable(PsiDocToken.class, hintClass) || ReflectionUtil.isAssignable(XmlToken.class, hintClass);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiElement) {
      final ASTNode node = ((PsiElement)element).getNode();
      return node != null && node.getElementType() == myType;
    }
    else if(element instanceof ASTNode){
      return ((ASTNode)element).getElementType() == myType;
    }
    return false;
  }

  public String toString(){
    return "token-type(" + myType + ")";
  }
}
