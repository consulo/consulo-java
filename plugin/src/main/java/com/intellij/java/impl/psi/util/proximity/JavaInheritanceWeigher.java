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
package com.intellij.java.impl.psi.util.proximity;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.util.NotNullFunction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.proximity.ProximityLocation;
import consulo.language.util.proximity.ProximityWeigher;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.NotNullLazyKey;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author peter
 */
@ExtensionImpl(id = "javaInheritance", order = "after explicitlyImported, before sameLogicalRoot")
public class JavaInheritanceWeigher extends ProximityWeigher {
    private static final NotNullLazyKey<Set<String>, ProximityLocation> PLACE_SUPER_CLASSES = NotNullLazyKey.create("PLACE_SUPER_CLASSES", new NotNullFunction<ProximityLocation, Set<String>>() {
        @Nonnull
        @Override
        public Set<String> apply(ProximityLocation location) {
            final HashSet<String> result = new HashSet<>();
            PsiClass contextClass = PsiTreeUtil.getContextOfType(location.getPosition(), PsiClass.class, false);
            Predicate<PsiClass> processor = psiClass ->  {
                ContainerUtil.addIfNotNull(result, psiClass.getQualifiedName());
                return true;
            };
            while (contextClass != null) {
                InheritanceUtil.processSupers(contextClass, true, processor);
                contextClass = contextClass.getContainingClass();
            }
            return result;
        }
    });

    @Override
    public Comparable weigh(@Nonnull final PsiElement element, @Nonnull final ProximityLocation location) {
        if (location.getPosition() == null || !(element instanceof PsiClass)) {
            return null;
        }
        if (isTooGeneral((PsiClass) element)) {
            return false;
        }

        Set<String> superClasses = PLACE_SUPER_CLASSES.getValue(location);
        if (superClasses.isEmpty()) {
            return false;
        }

        final PsiElement position = location.getPosition();
        PsiClass placeClass = findPlaceClass(element, position);
        if (placeClass == null) {
            return false;
        }

        PsiClass elementClass = placeClass;
        while (elementClass != null) {
            if (superClasses.contains(elementClass.getQualifiedName())) {
                return true;
            }
            elementClass = elementClass.getContainingClass();
        }

        return false;
    }

    @Nullable
    private static PsiClass findPlaceClass(PsiElement element, PsiElement position) {
        if (position.getParent() instanceof PsiReferenceExpression) {
            final PsiExpression qualifierExpression = ((PsiReferenceExpression) position.getParent()).getQualifierExpression();
            if (qualifierExpression != null) {
                final PsiType type = qualifierExpression.getType();
                if (type instanceof PsiClassType) {
                    final PsiClass psiClass = ((PsiClassType) type).resolve();
                    if (psiClass != null) {
                        return psiClass;
                    }
                }
            }
        }
        return PsiTreeUtil.getContextOfType(element, PsiClass.class, false);
    }

    private static boolean isTooGeneral(@Nullable final PsiClass element) {
        if (element == null) {
            return true;
        }

        @NonNls final String qname = element.getQualifiedName();
        return qname == null || qname.startsWith("java.lang.");
    }
}
