// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;

import java.util.*;

public final class TypeConstraints {
    /**
     * Top constraint (no restriction; any non-primitive value satisfies this)
     */
    public static final TypeConstraint TOP = new TypeConstraint() {
        @Nonnull
        @Override
        public TypeConstraint join(@Nonnull TypeConstraint other) {
            return this;
        }

        @Nonnull
        @Override
        public TypeConstraint meet(@Nonnull TypeConstraint other) {
            return other;
        }

        @Override
        public boolean isSuperConstraintOf(@Nonnull TypeConstraint other) {
            return true;
        }

        @Override
        public TypeConstraint tryNegate() {
            return BOTTOM;
        }

        @Override
        public String toString() {
            return "";
        }
    };
    /**
     * Bottom constraint (no actual type satisfies this)
     */
    public static final TypeConstraint BOTTOM = new TypeConstraint() {
        @Nonnull
        @Override
        public TypeConstraint join(@Nonnull TypeConstraint other) {
            return other;
        }

        @Nonnull
        @Override
        public TypeConstraint meet(@Nonnull TypeConstraint other) {
            return this;
        }

        @Override
        public boolean isSuperConstraintOf(@Nonnull TypeConstraint other) {
            return other == this;
        }

        @Override
        public TypeConstraint tryNegate() {
            return TOP;
        }

        @Override
        public String toString() {
            return "<impossible type>";
        }
    };

    /**
     * Exactly java.lang.Object class
     */
    public static final TypeConstraint.Exact EXACTLY_OBJECT = new TypeConstraint.Exact() {
        @Override
        public StreamEx<Exact> superTypes() {
            return StreamEx.empty();
        }

        @Override
        public boolean isFinal() {
            return false;
        }

        @Override
        public boolean isAssignableFrom(@Nonnull Exact other) {
            return true;
        }

        @Override
        public boolean isConvertibleFrom(@Nonnull Exact other) {
            return true;
        }

        @Nonnull
        @Override
        public TypeConstraint instanceOf() {
            return TOP;
        }

        @Nonnull
        @Override
        public TypeConstraint notInstanceOf() {
            return BOTTOM;
        }

        @Override
        public String toString() {
            return CommonClassNames.JAVA_LANG_OBJECT;
        }

        @Override
        public PsiType getPsiType(Project project) {
            return JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT);
        }
    };

    @Nullable
    private static TypeConstraint.Exact createExact(@Nonnull PsiType type) {
        if (type instanceof PsiArrayType arrayType) {
            PsiType componentType = arrayType.getComponentType();
            if (componentType instanceof PsiPrimitiveType) {
                for (PrimitiveArray p : PrimitiveArray.values()) {
                    if (p.getType().equals(componentType)) {
                        return p;
                    }
                }
                return null;
            }
            TypeConstraint.Exact componentConstraint = createExact(componentType);
            return componentConstraint == null ? null : new ExactArray(componentConstraint);
        }
        if (type instanceof PsiClassType classType) {
            PsiClass psiClass = classType.resolve();
            if (psiClass == null) {
                return new Unresolved(type.getCanonicalText());
            }
            if (!(psiClass instanceof PsiTypeParameter)) {
                return exactClass(psiClass);
            }
        }
        return null;
    }

    /**
     * @param type PsiType
     * @return a constraint for the object that has exactly given PsiType;
     * {@link #BOTTOM} if the object of given type cannot be instantiated.
     */
    @Nonnull
    @Contract(pure = true)
    public static TypeConstraint exact(@Nonnull PsiType type) {
        type = normalizeType(type);
        TypeConstraint.Exact exact = createExact(type);
        if (exact != null && exact.canBeInstantiated()) {
            return exact;
        }
        return BOTTOM;
    }

    /**
     * @param type PsiType
     * @return a constraint for the object whose type is the supplied type or any subtype
     */
    @Nonnull
    @Contract(pure = true)
    public static TypeConstraint instanceOf(@Nonnull PsiType type) {
        if (type instanceof PsiLambdaExpressionType || type instanceof PsiMethodReferenceType) {
            return TOP;
        }
        type = normalizeType(type);
        if (type instanceof PsiDisjunctionType disjunctionType) {
            type = disjunctionType.getLeastUpperBound();
        }
        if (type instanceof PsiIntersectionType intersectionType) {
            PsiType[] conjuncts = intersectionType.getConjuncts();
            TypeConstraint result = TOP;
            for (PsiType conjunct : conjuncts) {
                TypeConstraint.Exact exact = createExact(conjunct);
                if (exact == null) {
                    return new Unresolved(type.getCanonicalText()).instanceOf();
                }
                result = result.meet(exact.instanceOf());
            }
            return result;
        }
        TypeConstraint.Exact exact = createExact(type);
        if (exact == null) {
            return new Unresolved(type.getCanonicalText()).instanceOf();
        }
        return exact.instanceOf();
    }

    @Nonnull
    private static PsiType normalizeType(@Nonnull PsiType psiType) {
        if (psiType instanceof PsiArrayType) {
            return PsiTypesUtil.createArrayType(normalizeType(psiType.getDeepComponentType()), psiType.getArrayDimensions());
        }
        if (psiType instanceof PsiWildcardType wildcardType) {
            return normalizeType(wildcardType.getExtendsBound());
        }
        if (psiType instanceof PsiCapturedWildcardType capturedWildcardType) {
            return normalizeType(capturedWildcardType.getUpperBound());
        }
        if (psiType instanceof PsiIntersectionType intersectionType) {
            PsiType[] types = StreamEx.of(intersectionType.getConjuncts())
                .map(TypeConstraints::normalizeType)
                .toArray(PsiType.EMPTY_ARRAY);
            if (types.length > 0) {
                return PsiIntersectionType.createIntersection(true, types);
            }
        }
        if (psiType instanceof PsiClassType classType) {
            return normalizeClassType(classType, new HashSet<>());
        }
        return psiType;
    }

    @Nonnull
    private static PsiType normalizeClassType(@Nonnull PsiClassType psiType, Set<PsiClass> processed) {
        PsiClass aClass = psiType.resolve();
        if (aClass instanceof PsiTypeParameter) {
            PsiClassType[] types = aClass.getExtendsListTypes();
            List<PsiType> result = new ArrayList<>();
            for (PsiClassType type : types) {
                PsiClass resolved = type.resolve();
                if (resolved != null && processed.add(resolved)) {
                    PsiClassType classType = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(resolved);
                    result.add(normalizeClassType(classType, processed));
                }
            }
            if (!result.isEmpty()) {
                return PsiIntersectionType.createIntersection(true, result.toArray(PsiType.EMPTY_ARRAY));
            }
            return PsiType.getJavaLangObject(aClass.getManager(), aClass.getResolveScope());
        }
        return psiType.rawType();
    }

    @Nonnull
    private static TypeConstraint.Exact exactClass(@Nonnull PsiClass psiClass) {
        String name = psiClass.getQualifiedName();
        if (name != null) {
            switch (name) {
                case CommonClassNames.JAVA_LANG_OBJECT:
                    return EXACTLY_OBJECT;
                case CommonClassNames.JAVA_LANG_CLONEABLE:
                    return ArraySuperInterface.CLONEABLE;
                case CommonClassNames.JAVA_IO_SERIALIZABLE:
                    return ArraySuperInterface.SERIALIZABLE;
            }
        }
        return new ExactClass(psiClass);
    }

    private enum PrimitiveArray implements TypeConstraint.Exact {
        BOOLEAN(PsiType.BOOLEAN),
        INT(PsiType.INT),
        BYTE(PsiType.BYTE),
        SHORT(PsiType.SHORT),
        LONG(PsiType.LONG),
        CHAR(PsiType.CHAR),
        FLOAT(PsiType.FLOAT),
        DOUBLE(PsiType.DOUBLE);
        private final PsiPrimitiveType myType;

        PrimitiveArray(PsiPrimitiveType type) {
            myType = type;
        }

        @Nonnull
        @Override
        public PsiType getPsiType(Project project) {
            return myType.createArrayType();
        }

        @Nonnull
        @Override
        public String toString() {
            return myType.getCanonicalText() + "[]";
        }

        PsiPrimitiveType getType() {
            return myType;
        }

        @Override
        public boolean isFinal() {
            return true;
        }

        @Override
        public StreamEx<Exact> superTypes() {
            return StreamEx.<Exact>of(ArraySuperInterface.values()).append(EXACTLY_OBJECT);
        }

        @Override
        public boolean isAssignableFrom(@Nonnull Exact other) {
            return other.equals(this);
        }

        @Override
        public boolean isConvertibleFrom(@Nonnull Exact other) {
            return other.equals(this) || other.isAssignableFrom(this);
        }
    }

    private enum ArraySuperInterface implements TypeConstraint.Exact {
        CLONEABLE(CommonClassNames.JAVA_LANG_CLONEABLE),
        SERIALIZABLE(CommonClassNames.JAVA_IO_SERIALIZABLE);
        @Nonnull
        private final String myReference;

        ArraySuperInterface(@Nonnull String reference) {
            myReference = reference;
        }

        @Nonnull
        @Override
        public PsiType getPsiType(Project project) {
            return JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(myReference);
        }

        @Nonnull
        @Override
        public String toString() {
            return myReference;
        }

        @Override
        public boolean isFinal() {
            return false;
        }

        @Override
        public StreamEx<Exact> superTypes() {
            return StreamEx.of(EXACTLY_OBJECT);
        }

        @Override
        public boolean isAssignableFrom(@Nonnull Exact other) {
            if (equals(other)) {
                return true;
            }
            if (other instanceof PrimitiveArray || other instanceof ExactArray || other instanceof Unresolved) {
                return true;
            }
            //noinspection SimplifiableIfStatement
            if (other instanceof ExactClass exactClass) {
                return InheritanceUtil.isInheritor(exactClass.myClass, myReference);
            }
            return false;
        }

        @Override
        public boolean isConvertibleFrom(@Nonnull Exact other) {
            return !other.isFinal() || isAssignableFrom(other);
        }

        @Override
        public boolean canBeInstantiated() {
            return false;
        }
    }

    private static final class ExactClass implements TypeConstraint.Exact {
        private final
        @Nonnull
        PsiClass myClass;

        ExactClass(@Nonnull PsiClass aClass) {
            assert !(aClass instanceof PsiTypeParameter);
            myClass = aClass;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this
                || obj instanceof ExactClass exactClass && myClass.getManager().areElementsEquivalent(myClass, exactClass.myClass);
        }

        @Override
        @RequiredReadAction
        public int hashCode() {
            return Objects.hashCode(myClass.getName());
        }

        @Override
        public boolean canBeInstantiated() {
            // Abstract final type is incorrect. We, however, assume that final wins: it can be instantiated
            // otherwise TypeConstraints.instanceOf(type) would return impossible type
            return (myClass.isFinal() || !myClass.isAbstract())
                && !CommonClassNames.JAVA_LANG_VOID.equals(myClass.getQualifiedName());
        }

        @Override
        public boolean isComparedByEquals() {
            String name = myClass.getQualifiedName();
            return name != null && (CommonClassNames.JAVA_LANG_STRING.equals(name) || TypeConversionUtil.isPrimitiveWrapper(name));
        }

        @Nonnull
        @Override
        public PsiType getPsiType(Project project) {
            return JavaPsiFacade.getElementFactory(project).createType(myClass);
        }

        @Nonnull
        @Override
        @RequiredReadAction
        public String toString() {
            String name = myClass.getQualifiedName();
            if (name == null) {
                name = myClass.getName();
            }
            if (name == null && myClass instanceof PsiAnonymousClass anonymousClass) {
                PsiClassType baseClassType = anonymousClass.getBaseClassType();
                name = "anonymous " + createExact(baseClassType);
            }
            return String.valueOf(name);
        }

        @Override
        public boolean isFinal() {
            return myClass.isFinal();
        }

        @Override
        public StreamEx<Exact> superTypes() {
            List<Exact> superTypes = new ArrayList<>();
            InheritanceUtil.processSupers(myClass, false, t -> {
                if (!t.isFinal()) {
                    superTypes.add(exactClass(t));
                }
                return true;
            });
            return StreamEx.of(superTypes);
        }

        @Override
        public boolean isAssignableFrom(@Nonnull Exact other) {
            if (equals(other) || other instanceof Unresolved) {
                return true;
            }
            //noinspection SimplifiableIfStatement
            if (other instanceof ExactClass exactClass) {
                return InheritanceUtil.isInheritorOrSelf(exactClass.myClass, myClass, true);
            }
            return false;
        }

        @Override
        public boolean isConvertibleFrom(@Nonnull Exact other) {
            if (equals(other) || other instanceof Unresolved || other == EXACTLY_OBJECT) {
                return true;
            }
            if (other instanceof ArraySuperInterface arraySuperInterface) {
                return myClass.isInterface() || !myClass.isFinal()
                    || InheritanceUtil.isInheritor(myClass, arraySuperInterface.myReference);
            }
            if (other instanceof ExactClass exactClass) {
                PsiClass otherClass = exactClass.myClass;
                if (myClass.isInterface() && otherClass.isInterface()) {
                    return true;
                }
                if (myClass.isInterface() && !otherClass.isFinal()) {
                    return true;
                }
                if (otherClass.isInterface() && !myClass.isFinal()) {
                    return true;
                }
                PsiManager manager = myClass.getManager();
                return manager.areElementsEquivalent(myClass, otherClass)
                    || otherClass.isInheritor(myClass, true)
                    || myClass.isInheritor(otherClass, true);
            }
            return false;
        }
    }

    private static final class ExactArray implements TypeConstraint.Exact {
        private final
        @Nonnull
        Exact myComponent;

        private ExactArray(@Nonnull Exact component) {
            myComponent = component;
        }

        @Nullable
        @Override
        public PsiType getPsiType(Project project) {
            PsiType componentType = myComponent.getPsiType(project);
            return componentType == null ? null : componentType.createArrayType();
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj instanceof ExactArray exactArray && myComponent.equals(exactArray.myComponent);
        }

        @Override
        public int hashCode() {
            return myComponent.hashCode() * 31 + 1;
        }

        @Nonnull
        @Override
        public String toString() {
            return myComponent + "[]";
        }

        @Override
        public boolean isFinal() {
            return myComponent.isFinal();
        }

        @Override
        public StreamEx<Exact> superTypes() {
            return myComponent.superTypes().<Exact>map(ExactArray::new).append(ArraySuperInterface.values()).append(EXACTLY_OBJECT);
        }

        @Override
        public boolean isAssignableFrom(@Nonnull Exact other) {
            return other instanceof ExactArray exactArray && myComponent.isAssignableFrom(exactArray.myComponent);
        }

        @Override
        public boolean isConvertibleFrom(@Nonnull Exact other) {
            if (other instanceof ExactArray exactArray) {
                return myComponent.isConvertibleFrom(exactArray.myComponent);
            }
            if (other instanceof ArraySuperInterface) {
                return true;
            }
            //noinspection SimplifiableIfStatement
            if (other instanceof ExactClass exactClass) {
                return CommonClassNames.JAVA_LANG_OBJECT.equals(exactClass.myClass.getQualifiedName());
            }
            return false;
        }

        @Override
        public
        @Nonnull
        Exact getArrayComponent() {
            return myComponent;
        }
    }

    private static final class Unresolved implements TypeConstraint.Exact {
        private final
        @Nonnull
        String myReference;

        private Unresolved(@Nonnull String reference) {
            myReference = reference;
        }

        @Override
        public boolean isResolved() {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj instanceof Unresolved unresolved && myReference.equals(unresolved.myReference);
        }

        @Override
        public int hashCode() {
            return myReference.hashCode();
        }

        @Nonnull
        @Override
        public String toString() {
            return "<unresolved> " + myReference;
        }

        @Override
        public boolean isFinal() {
            return false;
        }

        @Override
        public StreamEx<Exact> superTypes() {
            return StreamEx.of(EXACTLY_OBJECT);
        }

        @Override
        public boolean isAssignableFrom(@Nonnull Exact other) {
            return other instanceof Unresolved || other instanceof ExactClass;
        }

        @Override
        public boolean isConvertibleFrom(@Nonnull Exact other) {
            return other instanceof Unresolved || other instanceof ExactClass || other instanceof ArraySuperInterface;
        }
    }
}
