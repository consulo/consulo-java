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
package com.intellij.refactoring.move.moveMembers;

import com.intellij.java.impl.refactoring.move.moveMembers.MoveMembersOptions;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiModifier;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author dyoma
 */
public class MockMoveMembersOptions implements MoveMembersOptions {
  private final PsiMember[] mySelectedMembers;
  private final String myTargetClassName;
  private String myMemberVisibility = PsiModifier.PUBLIC;

  public MockMoveMembersOptions(String targetClassName, PsiMember[] selectedMembers) {
    mySelectedMembers = selectedMembers;
    myTargetClassName = targetClassName;
  }

  public MockMoveMembersOptions(String targetClassName, Collection<PsiMember> memberSet) {
    this(targetClassName, memberSet.toArray(new PsiMember[memberSet.size()]));
  }

  @Override
  public String getMemberVisibility() {
    return myMemberVisibility;
  }

  @Override
  public boolean makeEnumConstant() {
    return true;
  }

  public void setMemberVisibility(@Nullable String visibility) {
    myMemberVisibility = visibility;
  }

  @Override
  public PsiMember[] getSelectedMembers() {
    return mySelectedMembers;
  }

  @Override
  public String getTargetClassName() {
    return myTargetClassName;
  }
}
