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
package com.intellij.java.impl.refactoring.util.classMembers;

import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;

public class ClassMembersUtil {
  public static boolean isProperMember(MemberInfoBase memberInfo) {
    final PsiElement member = memberInfo.getMember();
    return member instanceof PsiField || member instanceof PsiMethod
                || (member instanceof PsiClass && memberInfo.getOverrides() == null);
  }

  public static boolean isImplementedInterface(MemberInfoBase memberInfo) {
    return memberInfo.getMember() instanceof PsiClass && Boolean.FALSE.equals(memberInfo.getOverrides());
  }
}
