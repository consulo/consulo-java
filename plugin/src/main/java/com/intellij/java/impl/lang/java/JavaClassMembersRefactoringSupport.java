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
package com.intellij.java.impl.lang.java;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.refactoring.classMember.ClassMembersRefactoringSupport;
import consulo.language.editor.refactoring.classMember.DependentMembersCollectorBase;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import com.intellij.java.impl.refactoring.util.classMembers.ClassMembersUtil;
import com.intellij.java.impl.refactoring.util.classMembers.DependentMembersCollector;

import javax.annotation.Nonnull;

/**
 * @author Dennis.Ushakov
 */
@ExtensionImpl
public class JavaClassMembersRefactoringSupport implements ClassMembersRefactoringSupport {
  @Override
  public DependentMembersCollectorBase createDependentMembersCollector(Object clazz, Object superClass) {
    return new DependentMembersCollector((PsiClass)clazz, (PsiClass)superClass);
  }

  @Override
  public boolean isProperMember(MemberInfoBase member) {
    return ClassMembersUtil.isProperMember(member);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
