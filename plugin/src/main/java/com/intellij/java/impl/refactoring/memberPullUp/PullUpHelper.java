/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.memberPullUp;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.psi.PsiElement;

import java.util.LinkedHashSet;

/**
 * Created by Max Medvedev on 10/4/13
 */
public interface PullUpHelper<T extends MemberInfoBase<? extends PsiMember>> {
  void encodeContextInfo(T info);

  void move(T info, PsiSubstitutor substitutor);

  void postProcessMember(PsiMember member);

  void setCorrectVisibility(T info);

  void moveFieldInitializations(LinkedHashSet<PsiField> movedFields);

  void updateUsage(PsiElement element);
}
