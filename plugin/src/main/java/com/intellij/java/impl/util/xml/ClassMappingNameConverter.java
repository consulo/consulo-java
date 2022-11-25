/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.util.xml;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;
import consulo.xml.util.xml.*;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 * @see MappingClass
 */
public class ClassMappingNameConverter extends ResolvingConverter.StringConverter {

  @Nonnull
  @Override
  public Collection<? extends String> getVariants(ConvertContext context) {
    DomElement parent = context.getInvocationElement().getParent();
    assert parent != null;
    List<DomElement> children = DomUtil.getDefinedChildren(parent, true, true);
    DomElement classElement = ContainerUtil.find(children, new Condition<DomElement>() {
      @Override
      public boolean value(DomElement domElement) {
        return domElement.getAnnotation(MappingClass.class) != null;
      }
    });
    if (classElement == null) return Collections.emptyList();
    Object value = ((GenericDomValue) classElement).getValue();
    if (value == null) return Collections.emptyList();
    assert value instanceof PsiClass : classElement + " should have PsiClass type";
    PsiClass psiClass = (PsiClass) value;

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(context.getProject());
    PsiClassType classType = PsiTypesUtil.getClassType(psiClass);
    SuggestedNameInfo info = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, classType);
    return Arrays.asList(info.names);
  }

  @Override
  public PsiElement resolve(String o, ConvertContext context) {
    DomElement parent = context.getInvocationElement().getParent();
    assert parent != null;
    return parent.getXmlElement();
  }
}
