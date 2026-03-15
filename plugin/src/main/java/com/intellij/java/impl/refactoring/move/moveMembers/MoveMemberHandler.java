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
import consulo.localize.LocalizeValue;
import consulo.util.collection.MultiMap;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MoveMemberHandler extends LanguageExtension {
    ExtensionPointCacheKey<MoveMemberHandler, ByLanguageValue<MoveMemberHandler>> KEY =
        ExtensionPointCacheKey.create("MoveMemberHandler", LanguageOneToOne.build());

    @Nullable
    static MoveMemberHandler forLanguage(Language language) {
        return Application.get().getExtensionPoint(MoveMemberHandler.class).getOrBuildCache(KEY).get(language);
    }

    MoveMembersProcessor.@Nullable MoveMembersUsageInfo getUsage(
        PsiMember member, PsiReference ref, Set<PsiMember> membersToMove,
        PsiClass targetClass
    );

    void checkConflictsOnUsage(
        MoveMembersProcessor.MoveMembersUsageInfo usageInfo,
        @Nullable String newVisibility,
        @Nullable PsiModifierList modifierListCopy,
        PsiClass targetClass,
        Set<PsiMember> membersToMove,
        MultiMap<PsiElement, LocalizeValue> conflicts
    );

    void checkConflictsOnMember(
        PsiMember member,
        @Nullable String newVisibility,
        @Nullable PsiModifierList modifierListCopy,
        PsiClass targetClass,
        Set<PsiMember> membersToMove,
        MultiMap<PsiElement, LocalizeValue> conflicts
    );

    @Nullable
    PsiElement getAnchor(PsiMember member, PsiClass targetClass, Set<PsiMember> membersToMove);

    boolean changeExternalUsage(MoveMembersOptions options, MoveMembersProcessor.MoveMembersUsageInfo usage);

    PsiMember doMove(
        MoveMembersOptions options,
        PsiMember member,
        @Nullable PsiElement anchor,
        PsiClass targetClass
    );

    void decodeContextInfo(PsiElement scope);
}
