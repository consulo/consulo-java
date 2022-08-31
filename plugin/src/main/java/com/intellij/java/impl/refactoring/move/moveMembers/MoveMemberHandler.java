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
package com.intellij.java.impl.refactoring.move.moveMembers;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.lang.LanguageExtension;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.psi.PsiReference;
import com.intellij.util.containers.MultiMap;

/**
 * @author Maxim.Medvedev
 */
public interface MoveMemberHandler
{
	LanguageExtension<MoveMemberHandler> EP_NAME = new LanguageExtension<MoveMemberHandler>("consulo.java.refactoring.moveMemberHandler");

	@Nullable
	MoveMembersProcessor.MoveMembersUsageInfo getUsage(@Nonnull PsiMember member, @Nonnull PsiReference ref, @Nonnull Set<PsiMember> membersToMove,
			@Nonnull PsiClass targetClass);

	void checkConflictsOnUsage(@Nonnull MoveMembersProcessor.MoveMembersUsageInfo usageInfo, @javax.annotation.Nullable String newVisibility,
			@Nullable PsiModifierList modifierListCopy, @Nonnull PsiClass targetClass, @Nonnull Set<PsiMember> membersToMove,
			@Nonnull MultiMap<PsiElement, String> conflicts);

	void checkConflictsOnMember(@Nonnull PsiMember member, @Nullable String newVisibility, @Nullable PsiModifierList modifierListCopy,
			@Nonnull PsiClass targetClass, @Nonnull Set<PsiMember> membersToMove, @Nonnull MultiMap<PsiElement, String> conflicts);

	@Nullable
	PsiElement getAnchor(@Nonnull PsiMember member, @Nonnull PsiClass targetClass, Set<PsiMember> membersToMove);

	boolean changeExternalUsage(@Nonnull MoveMembersOptions options, @Nonnull MoveMembersProcessor.MoveMembersUsageInfo usage);

	@Nonnull
	PsiMember doMove(@Nonnull MoveMembersOptions options, @Nonnull PsiMember member, @javax.annotation.Nullable PsiElement anchor, @Nonnull PsiClass targetClass);

	void decodeContextInfo(@Nonnull PsiElement scope);
}
