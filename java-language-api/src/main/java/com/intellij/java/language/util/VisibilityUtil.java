/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 07.06.2002
 * Time: 18:48:01
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.language.util;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiBundle;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

public class VisibilityUtil {
    public static final String ESCALATE_VISIBILITY = "EscalateVisible";
    private static final String[] visibilityModifiers = {
        PsiModifier.PRIVATE,
        PsiModifier.PACKAGE_LOCAL,
        PsiModifier.PROTECTED,
        PsiModifier.PUBLIC
    };

    private VisibilityUtil() {
    }

    public static int compare(@PsiModifier.ModifierConstant String v1, @PsiModifier.ModifierConstant String v2) {
        return ArrayUtil.find(visibilityModifiers, v2) - ArrayUtil.find(visibilityModifiers, v1);
    }

    @PsiModifier.ModifierConstant
    public static String getHighestVisibility(@PsiModifier.ModifierConstant String v1, @PsiModifier.ModifierConstant String v2) {
        return compare(v1, v2) < 0 ? v1 : v2;
    }

    public static void escalateVisibility(PsiMember modifierListOwner, PsiElement place) throws IncorrectOperationException {
        String visibilityModifier = getVisibilityModifier(modifierListOwner.getModifierList());
        int index;
        for (index = 0; index < visibilityModifiers.length; index++) {
            String modifier = visibilityModifiers[index];
            if (modifier.equals(visibilityModifier)) {
                break;
            }
        }
        for (; index < visibilityModifiers.length && !PsiUtil.isAccessible(modifierListOwner, place, null); index++) {
            @PsiModifier.ModifierConstant String modifier = visibilityModifiers[index];
            PsiUtil.setModifierProperty(modifierListOwner, modifier, true);
        }
    }

    public static void escalateVisibility(PsiModifierList modifierList, PsiElement place) throws IncorrectOperationException {
        PsiElement parent = modifierList.getParent();
        if (parent instanceof PsiMember) {
            escalateVisibility((PsiMember)parent, place);
        }
    }

    @PsiModifier.ModifierConstant
    public static String getPossibleVisibility(PsiMember psiMethod, PsiElement place) {
        Project project = psiMethod.getProject();
        if (PsiUtil.isAccessible(project, psiMethod, place, null)) {
            return getVisibilityModifier(psiMethod.getModifierList());
        }
        if (JavaPsiFacade.getInstance(project).arePackagesTheSame(psiMethod, place)) {
            return PsiModifier.PACKAGE_LOCAL;
        }
        if (InheritanceUtil.isInheritorOrSelf(PsiTreeUtil.getParentOfType(place, PsiClass.class), psiMethod.getContainingClass(), true)) {
            return PsiModifier.PROTECTED;
        }
        return PsiModifier.PUBLIC;
    }

    @PsiModifier.ModifierConstant
    public static String getVisibilityModifier(PsiModifierList list) {
        if (list == null) {
            return PsiModifier.PACKAGE_LOCAL;
        }
        for (@PsiModifier.ModifierConstant String modifier : visibilityModifiers) {
            if (list.hasModifierProperty(modifier)) {
                return modifier;
            }
        }
        return PsiModifier.PACKAGE_LOCAL;
    }

    @Nonnull
    public static String getVisibilityString(@PsiModifier.ModifierConstant String visibilityModifier) {
        if (PsiModifier.PACKAGE_LOCAL.equals(visibilityModifier)) {
            return "";
        }
        return visibilityModifier;
    }

    @Nls
    @Nonnull
    public static String getVisibilityStringToDisplay(@Nonnull PsiMember member) {
        if (member.isPublic()) {
            return toPresentableText(PsiModifier.PUBLIC);
        }
        if (member.isProtected()) {
            return toPresentableText(PsiModifier.PROTECTED);
        }
        if (member.isPrivate()) {
            return toPresentableText(PsiModifier.PRIVATE);
        }
        return toPresentableText(PsiModifier.PACKAGE_LOCAL);
    }

    @Nonnull
    public static String toPresentableText(@PsiModifier.ModifierConstant @Nonnull String modifier) {
        return PsiBundle.visibilityPresentation(modifier);
    }

    public static void fixVisibility(PsiElement[] elements, PsiMember member, @PsiModifier.ModifierConstant String newVisibility) {
        if (newVisibility == null) {
            return;
        }
        if (ESCALATE_VISIBILITY.equals(newVisibility)) {
            for (PsiElement element : elements) {
                if (element != null) {
                    escalateVisibility(member, element);
                }
            }
        }
        else {
            setVisibility(member.getModifierList(), newVisibility);
        }
    }

    public static void setVisibility(
        PsiModifierList modifierList,
        @PsiModifier.ModifierConstant String newVisibility
    ) throws IncorrectOperationException {
        modifierList.setModifierProperty(newVisibility, true);
    }

    public static void fixVisibility(PsiExpression[] expressions, PsiMember member, String newVisibility) {
        if (newVisibility == null) {
            return;
        }
        if (ESCALATE_VISIBILITY.equals(newVisibility)) {
            for (PsiExpression element : expressions) {
                escalateVisibility(member, element);
            }
        }
        else {
            setVisibility(member.getModifierList(), newVisibility);
        }
    }
}
