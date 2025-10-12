/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.localize.LocalizeValue;

import java.util.List;

/**
 * User: anna
 * Date: 7/12/12
 */
public class CreateGetterSetterPropertyFromUsageFix extends CreatePropertyFromUsageFix {
  public CreateGetterSetterPropertyFromUsageFix(PsiMethodCallExpression methodCall) {
    super(methodCall);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    boolean available = super.isAvailableImpl(offset);
    if (available) {
      setText(LocalizeValue.localizeTODO("Create Property"));
    }
    return available;
  }

  @Override
  protected boolean checkTargetClasses(List<PsiClass> classes, String methodName) {
    String propertyName = PropertyUtil.getPropertyName(methodName);
    if (propertyName == null) return false;
    String getterName = PropertyUtil.suggestGetterName(propertyName, null);
    String setterName = PropertyUtil.suggestSetterName(propertyName);
    for (PsiClass aClass : classes) {
      if (aClass.findMethodsByName(getterName, false).length > 0 || aClass.findMethodsByName(setterName, false).length > 0) return false;
    }
    return true;
  }

  @Override
  protected void beforeTemplateFinished(PsiClass aClass, PsiField field) {
    PsiMethod getterPrototype = PropertyUtil.generateGetterPrototype(field);
    if (aClass.findMethodsBySignature(getterPrototype, false).length == 0) {
      aClass.add(getterPrototype);
    }


    PsiMethod setterPrototype = PropertyUtil.generateSetterPrototype(field);
    if (aClass.findMethodsBySignature(setterPrototype, false).length == 0) {
      aClass.add(setterPrototype);
    }
    
    super.beforeTemplateFinished(aClass, field);
  }
}
