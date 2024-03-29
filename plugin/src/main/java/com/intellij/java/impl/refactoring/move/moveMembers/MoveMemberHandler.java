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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiModifierList;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.Language;
import consulo.language.extension.ByLanguageValue;
import consulo.language.extension.LanguageExtension;
import consulo.language.extension.LanguageOneToOne;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.util.collection.MultiMap;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MoveMemberHandler extends LanguageExtension {
  ExtensionPointCacheKey<MoveMemberHandler, ByLanguageValue<MoveMemberHandler>> KEY = ExtensionPointCacheKey.create("MoveMemberHandler", LanguageOneToOne.build());

  @Nullable
  static MoveMemberHandler forLanguage(@Nonnull Language language) {
    return Application.get().getExtensionPoint(MoveMemberHandler.class).getOrBuildCache(KEY).get(language);
  }

  @Nullable
  MoveMembersProcessor.MoveMembersUsageInfo getUsage(@Nonnull PsiMember member, @Nonnull PsiReference ref, @Nonnull Set<PsiMember> membersToMove,
                                                     @Nonnull PsiClass targetClass);

  void checkConflictsOnUsage(@Nonnull MoveMembersProcessor.MoveMembersUsageInfo usageInfo, @Nullable String newVisibility,
                             @Nullable PsiModifierList modifierListCopy, @Nonnull PsiClass targetClass, @Nonnull Set<PsiMember> membersToMove,
                             @Nonnull MultiMap<PsiElement, String> conflicts);

  void checkConflictsOnMember(@Nonnull PsiMember member, @Nullable String newVisibility, @Nullable PsiModifierList modifierListCopy,
                              @Nonnull PsiClass targetClass, @Nonnull Set<PsiMember> membersToMove, @Nonnull MultiMap<PsiElement, String> conflicts);

  @Nullable
  PsiElement getAnchor(@Nonnull PsiMember member, @Nonnull PsiClass targetClass, Set<PsiMember> membersToMove);

  boolean changeExternalUsage(@Nonnull MoveMembersOptions options, @Nonnull MoveMembersProcessor.MoveMembersUsageInfo usage);

  @Nonnull
  PsiMember doMove(@Nonnull MoveMembersOptions options, @Nonnull PsiMember member, @Nullable PsiElement anchor, @Nonnull PsiClass targetClass);

  void decodeContextInfo(@Nonnull PsiElement scope);
}
