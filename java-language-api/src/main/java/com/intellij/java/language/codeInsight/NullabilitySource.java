// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.codeInsight;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Source for type nullability.
 */
public /* sealed */ interface NullabilitySource {
    enum Standard implements NullabilitySource {
        /**
         * Type nullability is not specified
         */
        NONE {
            @Override
            public NullabilitySource inherited() {
                return this;
            }
        },
        /**
         * Type nullability is mandated by language specification
         * (e.g., primitive type, or disjunction type)
         */
        MANDATED,
        /**
         * Type nullability is known from a particular code shape.
         * While it may differ from one defined by the language constructs only,
         * it's believed to be correct and more helpful to users.
         */
        KNOWN,
        /**
         * Type nullability is depicted explicitly by means of the language.
         * Currently, not possible in Java, but may be used in other languages like Kotlin.
         */
        LANGUAGE_DEFINED
    }

    /**
     * @return source of type nullability inherited from a bound
     * @see ExtendsBound
     */
    default NullabilitySource inherited() {
        return new ExtendsBound(this);
    }

    /**
     * Source of type nullability inherited from a bound.
     * <p>
     * Sometimes, it's important to distinguish.
     * E.g., consider the method return type for two declarations:
     * <ol>
     * <li>{@code <T> @Nullable T m()}
     * <li>{@code <T extends @Nullable Object> T m()}
     * </ol>
     * In both cases, return type nullability is {@code Nullable}.
     * However, with {@code T = @NotNull String} instantiation, the first should
     * produce {@code @Nullable String}, while the second should produce {@code @NotNull String}.
     */
    final class ExtendsBound implements NullabilitySource {
        private final NullabilitySource myBoundSource;

        public ExtendsBound(NullabilitySource source) {
            myBoundSource = source;
        }

        public NullabilitySource boundSource() {
            return myBoundSource;
        }

        @Override
        public ExtendsBound inherited() {
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ExtendsBound bound = (ExtendsBound) o;
            return myBoundSource.equals(bound.myBoundSource);
        }

        @Override
        public int hashCode() {
            return myBoundSource.hashCode();
        }

        @Override
        public String toString() {
            return "inherited " + myBoundSource;
        }
    }

    /**
     * Type nullability is explicitly specified by an annotation.
     * Annotation owner is normally the type.
     */
    final class ExplicitAnnotation implements NullabilitySource {
        private final PsiAnnotation myAnnotation;

        public ExplicitAnnotation(PsiAnnotation annotation) {
            myAnnotation = annotation;
        }

        public PsiAnnotation annotation() {
            return myAnnotation;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            ExplicitAnnotation that = (ExplicitAnnotation) o;
            return myAnnotation.equals(that.myAnnotation);
        }

        @Override
        public int hashCode() {
            return myAnnotation.hashCode();
        }

        @Override
        public String toString() {
            PsiJavaCodeReferenceElement ref = annotation().getNameReferenceElement();
            if (ref == null) return "@<unknown>";
            return "@" + ref.getReferenceName();
        }
    }

    /**
     * Type nullability is inherited from a container (member/class/package/module)
     */
    final class ContainerAnnotation implements NullabilitySource {
        private final PsiAnnotation myAnnotation;

        public ContainerAnnotation(PsiAnnotation annotation) {
            myAnnotation = annotation;
            container(); // validate
        }

        PsiElement container() {
            PsiModifierList modifierList = (PsiModifierList) requireNonNull(myAnnotation.getOwner(), "Annotation has no owner");
            PsiElement owner = requireNonNull(modifierList.getParent(), "Modifier list has no parent");
            if (owner instanceof PsiModifierListOwner) {
                PsiModifierListOwner member = (PsiModifierListOwner) owner;
                if (member.getModifierList() != modifierList) {
                    throw new IllegalStateException("Modifier list parent is incorrect");
                }
                return member;
            }
            else if (owner instanceof PsiPackageStatement) {
                PsiPackageStatement packageStatement = (PsiPackageStatement) owner;
                if (packageStatement.getAnnotationList() != modifierList) {
                    throw new IllegalStateException("Modifier list parent is incorrect");
                }
                return owner;
            }
            else {
                throw new IllegalStateException("Unsupported modifier list parent: " + owner.getClass().getName());
            }
        }

        public PsiAnnotation annotation() {
            return myAnnotation;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ContainerAnnotation that = (ContainerAnnotation) o;
            return myAnnotation.equals(that.myAnnotation);
        }

        @Override
        public int hashCode() {
            return myAnnotation.hashCode();
        }

        @Override
        public String toString() {
            PsiElement container = container();
            String containerInfo;
            if (container instanceof PsiClass) {
                containerInfo = "class " + ((PsiClass) container).getName();
            }
            else if (container instanceof PsiField) {
                containerInfo = "field " + ((PsiField) container).getName();
            }
            else if (container instanceof PsiMethod) {
                containerInfo = "method " + ((PsiMethod) container).getName();
            }
            else if (container instanceof PsiPackageStatement) {
                containerInfo = "package " + ((PsiPackageStatement) container).getPackageName();
            }
            else if (container instanceof PsiJavaModule) {
                containerInfo = "module " + ((PsiJavaModule) container).getName();
            }
            else {
                containerInfo = container.getClass().getSimpleName();
            }
            PsiJavaCodeReferenceElement ref = annotation().getNameReferenceElement();
            String annotationInfo = ref == null ? "@<unknown>" : "@" + ref.getReferenceName();
            return annotationInfo + " on " + containerInfo;
        }
    }

    final class MultiSource implements NullabilitySource {
        private final Set<NullabilitySource> mySources;

        MultiSource(Set<NullabilitySource> sources) {
            if (sources.size() <= 1) {
                throw new IllegalArgumentException("MultiSource must have at least two sources");
            }
            mySources = sources;
        }

        public Set<NullabilitySource> sources() {
            return mySources;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            MultiSource source = (MultiSource) o;
            return mySources.equals(source.mySources);
        }

        @Override
        public int hashCode() {
            return mySources.hashCode();
        }

        @Override
        public String toString() {
            return mySources.toString();
        }
    }

    /**
     * @param sources sources to combine
     * @return combined source, or {@link Standard#NONE} if no sources are specified
     */
    static NullabilitySource multiSource(Collection<NullabilitySource> sources) {
        Set<NullabilitySource> set = new LinkedHashSet<>();
        for (NullabilitySource source : sources) {
            if (source instanceof MultiSource) {
                set.addAll(((MultiSource) source).sources());
                continue;
            }
            if (source == Standard.NONE) continue;
            set.add(source);
        }
        if (set.isEmpty()) return Standard.NONE;
        if (set.size() == 1) return set.iterator().next();
        if (ContainerUtil.and(set, s -> s instanceof ExtendsBound)) {
            return new MultiSource(ContainerUtil.map2LinkedSet(set, s -> ((ExtendsBound) s).boundSource())).inherited();
        }
        return new MultiSource(set);
    }
}
