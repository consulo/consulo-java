// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.codeInsight;

import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeParameter;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class that represents nullability of a type, including the nullability itself, and the nullability source.
 */
public final class TypeNullability {
    /**
     * Unknown nullability without the source
     */
    public static final TypeNullability UNKNOWN = new TypeNullability(Nullability.UNKNOWN, NullabilitySource.Standard.NONE);
    /**
     * Mandated not-null nullability
     */
    public static final TypeNullability NOT_NULL_MANDATED = new TypeNullability(Nullability.NOT_NULL, NullabilitySource.Standard.MANDATED);
    /**
     * Known not-null nullability
     */
    public static final TypeNullability NOT_NULL_KNOWN = new TypeNullability(Nullability.NOT_NULL, NullabilitySource.Standard.KNOWN);
    /**
     * Mandated nullable nullability
     */
    public static final TypeNullability NULLABLE_MANDATED = new TypeNullability(Nullability.NULLABLE, NullabilitySource.Standard.MANDATED);

    private final @Nonnull Nullability myNullability;
    private final @Nonnull NullabilitySource mySource;

    public TypeNullability(@Nonnull Nullability nullability, @Nonnull NullabilitySource source) {
        myNullability = nullability;
        mySource = source;
        if (nullability != Nullability.UNKNOWN && source == NullabilitySource.Standard.NONE) {
            throw new IllegalArgumentException("Source must be specified for non-unknown nullability");
        }
    }

    /**
     * @return the nullability of the type
     */
    public @Nonnull Nullability nullability() {
        return myNullability;
    }

    /**
     * @return the source of the nullability information
     */
    public @Nonnull NullabilitySource source() {
        return mySource;
    }

    /**
     * @return the same nullability but marked as inherited from a bound.
     * @see NullabilitySource.ExtendsBound
     */
    public @Nonnull TypeNullability inherited() {
        NullabilitySource inherited = mySource.inherited();
        return inherited.equals(mySource) ? this : new TypeNullability(myNullability, inherited);
    }

    /**
     * @param nullability instantiation nullability
     * @return the nullability of the instantiated type parameter,
     * assuming that this object is the declared nullability of the type parameter.
     */
    public @Nonnull TypeNullability instantiatedWith(@Nonnull TypeNullability nullability) {
        if (this.nullability() == nullability.nullability()) {
            return nullability;
        }
        if (this.nullability() == Nullability.NOT_NULL) {
            return this;
        }
        if (this.nullability() == Nullability.NULLABLE && this.source() instanceof NullabilitySource.ExtendsBound) {
            return nullability;
        }
        if (nullability.nullability() == Nullability.NOT_NULL && this.source() instanceof NullabilitySource.ExtendsBound) {
            return nullability;
        }
        if (this.source() == NullabilitySource.Standard.NONE) {
            return nullability;
        }
        return this;
    }

    public @Nonnull TypeNullability join(@Nonnull TypeNullability other) {
        if (this.nullability() == other.nullability()) {
            if (this.source().equals(other.source())) return this;
            return new TypeNullability(this.nullability(), NullabilitySource.multiSource(Arrays.asList(this.source(), other.source())));
        }
        if (this.nullability() == Nullability.NULLABLE) {
            return this;
        }
        if (other.nullability() == Nullability.NULLABLE) {
            return other;
        }
        return this.nullability() == Nullability.UNKNOWN ? this : other;
    }

    public @Nonnull TypeNullability meet(@Nonnull TypeNullability other) {
        if (this.nullability() == other.nullability()) {
            if (this.source().equals(other.source())) return this;
            return new TypeNullability(this.nullability(), NullabilitySource.multiSource(Arrays.asList(this.source(), other.source())));
        }
        if (this.nullability() == Nullability.NOT_NULL) {
            return this;
        }
        if (other.nullability() == Nullability.NOT_NULL) {
            return other;
        }
        return this.nullability() == Nullability.NULLABLE ? this : other;
    }

    public static @Nonnull TypeNullability ofTypeParameter(@Nonnull PsiTypeParameter parameter) {
        TypeNullability nullability = intersect(ContainerUtil.map(parameter.getSuperTypes(), PsiType::getNullability)).inherited();
        if (!nullability.equals(UNKNOWN)) {
            return nullability;
        }
        NullableNotNullManager manager = NullableNotNullManager.getInstance(parameter.getProject());
        if (manager != null) {
            NullabilityAnnotationInfo effective = manager.findOwnNullabilityInfo(parameter);
            if (effective != null) {
                return effective.toTypeNullability().inherited();
            }
            NullabilityAnnotationInfo typeUseNullability = manager.findDefaultTypeUseNullability(parameter);
            if (typeUseNullability != null) {
                return typeUseNullability.toTypeNullability().inherited();
            }
        }
        return UNKNOWN;
    }

    /**
     * @param collection type nullabilities to intersect
     * @return the intersection of the type nullabilities in the collection
     */
    public static @Nonnull TypeNullability intersect(@Nonnull Collection<TypeNullability> collection) {
        if (collection.isEmpty()) return UNKNOWN;
        if (collection.size() == 1) return collection.iterator().next();
        Map<Nullability, Set<NullabilitySource>> map = collection.stream().collect(Collectors.groupingBy(
            TypeNullability::nullability, Collectors.mapping(TypeNullability::source, Collectors.toSet())));
        Set<NullabilitySource> sources = map.get(Nullability.NOT_NULL);
        if (sources != null) {
            return new TypeNullability(Nullability.NOT_NULL, NullabilitySource.multiSource(sources));
        }
        sources = map.get(Nullability.NULLABLE);
        if (sources != null) {
            return new TypeNullability(Nullability.NULLABLE, NullabilitySource.multiSource(sources));
        }
        sources = map.get(Nullability.UNKNOWN);
        if (sources != null) {
            return new TypeNullability(Nullability.UNKNOWN, NullabilitySource.multiSource(sources));
        }
        return UNKNOWN;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        TypeNullability that = (TypeNullability) o;
        return myNullability == that.myNullability && mySource.equals(that.mySource);
    }

    @Override
    public int hashCode() {
        int result = myNullability.hashCode();
        result = 31 * result + mySource.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return myNullability + " (" + mySource + ")";
    }

    /**
     * @return this object in the form of {@link NullabilityAnnotationInfo} if conversion is possible, null otherwise.
     */
    public @Nullable NullabilityAnnotationInfo toNullabilityAnnotationInfo() {
        NullabilitySource source = source();
        if (source instanceof NullabilitySource.ExtendsBound) {
            source = ((NullabilitySource.ExtendsBound) source).boundSource();
        }
        if (source instanceof NullabilitySource.MultiSource) {
            source = ((NullabilitySource.MultiSource) source).sources().iterator().next();
        }
        if (source instanceof NullabilitySource.ExplicitAnnotation) {
            return new NullabilityAnnotationInfo(((NullabilitySource.ExplicitAnnotation) source).annotation(), nullability(), false);
        }
        if (source instanceof NullabilitySource.ContainerAnnotation) {
            return new NullabilityAnnotationInfo(((NullabilitySource.ContainerAnnotation) source).annotation(), nullability(), true);
        }
        return null;
    }
}
