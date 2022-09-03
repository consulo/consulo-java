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
package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.language.impl.codeInsight.generation.PsiElementClassMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.ide.impl.psi.util.PsiFormatUtilBase;

/**
 * @author peter
*/
public class PsiMethodMember extends PsiElementClassMember<PsiMethod> {
  private static final int PARAM_OPTIONS = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER;
  private static final int METHOD_OPTIONS = PARAM_OPTIONS | PsiFormatUtilBase.SHOW_PARAMETERS;

  public PsiMethodMember(final PsiMethod method) {
    this(method, PsiSubstitutor.EMPTY);
  }

  public PsiMethodMember(CandidateInfo info) {
    this((PsiMethod)info.getElement(), info.getSubstitutor());
  }

  public PsiMethodMember(final PsiMethod method, final PsiSubstitutor substitutor) {
    super(method, substitutor, PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, METHOD_OPTIONS, PARAM_OPTIONS));
  }

}
