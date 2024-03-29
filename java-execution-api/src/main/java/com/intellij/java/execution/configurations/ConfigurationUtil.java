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
package com.intellij.java.execution.configurations;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.PsiMethodUtil;

import java.util.function.Predicate;

/**
 * User: anna
 * Date: Jan 26, 2005
 */
public class ConfigurationUtil {
  public static final Predicate<PsiClass> MAIN_CLASS = PsiMethodUtil.MAIN_CLASS;

  public static final Predicate<PsiClass> PUBLIC_INSTANTIATABLE_CLASS = psiClass -> MAIN_CLASS.test(psiClass) &&
         psiClass.hasModifierProperty(PsiModifier.PUBLIC) &&
         !psiClass.hasModifierProperty(PsiModifier.ABSTRACT);
}
