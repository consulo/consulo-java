// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypes;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.execution.debug.stream.trace.dsl.Types;
import consulo.execution.debug.stream.trace.impl.handler.type.*;
import jakarta.annotation.Nonnull;

import java.util.Set;
import java.util.function.Function;

/**
 * @author Vitaliy.Bibaev
 */
public final class JavaTypes implements Types {
    public static final JavaTypes INSTANCE = new JavaTypes();

    private final GenericType ANY = new ClassTypeImpl(CommonClassNames.JAVA_LANG_OBJECT, "new java.lang.Object()");
    private final GenericType INT = new GenericTypeImpl("int", CommonClassNames.JAVA_LANG_INTEGER, "0");
    private final GenericType BOOLEAN = new GenericTypeImpl("boolean", CommonClassNames.JAVA_LANG_BOOLEAN, "false");
    private final GenericType DOUBLE = new GenericTypeImpl("double", CommonClassNames.JAVA_LANG_DOUBLE, "0.");
    private final GenericType EXCEPTION = new ClassTypeImpl(CommonClassNames.JAVA_LANG_THROWABLE);
    private final GenericType VOID = new GenericTypeImpl("void", CommonClassNames.JAVA_LANG_VOID, "null");
    private final GenericType TIME = new ClassTypeImpl("java.util.concurrent.atomic.AtomicInteger",
                                                       "new java.util.concurrent.atomic.AtomicInteger()");
    private final GenericType STRING = new ClassTypeImpl(CommonClassNames.JAVA_LANG_STRING, "\"\"");
    private final GenericType LONG = new GenericTypeImpl("long", CommonClassNames.JAVA_LANG_LONG, "0L");

    private final GenericType optional = new ClassTypeImpl(CommonClassNames.JAVA_UTIL_OPTIONAL);
    private final GenericType optionalInt = new ClassTypeImpl("java.util.OptionalInt");
    private final GenericType optionalLong = new ClassTypeImpl("java.util.OptionalLong");
    private final GenericType optionalDouble = new ClassTypeImpl("java.util.OptionalDouble");

    private final Set<GenericType> OPTIONAL_TYPES = Set.of(optional, optionalInt, optionalLong, optionalDouble);

    private JavaTypes() {
    }

    @Nonnull
    @Override
    public GenericType ANY() {
        return ANY;
    }

    @Nonnull
    @Override
    public GenericType INT() {
        return INT;
    }

    @Nonnull
    @Override
    public GenericType BOOLEAN() {
        return BOOLEAN;
    }

    @Nonnull
    @Override
    public GenericType DOUBLE() {
        return DOUBLE;
    }

    @Nonnull
    @Override
    public GenericType EXCEPTION() {
        return EXCEPTION;
    }

    @Nonnull
    @Override
    public GenericType VOID() {
        return VOID;
    }

    @Nonnull
    @Override
    public GenericType TIME() {
        return TIME;
    }

    @Nonnull
    @Override
    public GenericType STRING() {
        return STRING;
    }

    @Nonnull
    @Override
    public GenericType LONG() {
        return LONG;
    }

    @Nonnull
    @Override
    public ArrayType array(@Nonnull GenericType elementType) {
        return new ArrayTypeImpl(elementType,
                                 name -> name + "[]",
                                 size -> "new " + elementType.getVariableTypeName() + "[" + size + "]");
    }

    @Nonnull
    @Override
    public MapType map(@Nonnull GenericType keyType, @Nonnull GenericType valueType) {
        return new MapTypeImpl(keyType, valueType,
                               (keys, values) -> "java.util.Map<" + keys + ", " + values + ">",
                               "new java.util.HashMap<>()",
                               (keys, values) -> "java.util.Map.Entry<" + keys + ", " + values + ">");
    }

    @Nonnull
    @Override
    public MapType linkedMap(@Nonnull GenericType keyType, @Nonnull GenericType valueType) {
        return new MapTypeImpl(keyType, valueType,
                               (keys, values) -> "java.util.Map<" + keys + ", " + values + ">",
                               "new java.util.LinkedHashMap<>()",
                               (keys, values) -> "java.util.Map.Entry<" + keys + ", " + values + ">");
    }

    @Nonnull
    @Override
    public ListType list(@Nonnull GenericType elementsType) {
        return new ListTypeImpl(elementsType,
                                type -> "java.util.List<" + type + ">",
                                "new java.util.ArrayList<>()");
    }

    @Nonnull
    @Override
    public GenericType nullable(@Nonnull Function<Types, GenericType> typeSelector) {
        return typeSelector.apply(this);
    }

    @Nonnull
    public GenericType fromStreamPsiType(@Nonnull PsiType streamPsiType) {
        if (InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) {
            return INT;
        }
        if (InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) {
            return LONG;
        }
        if (InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) {
            return DOUBLE;
        }
        if (PsiTypes.voidType().equals(streamPsiType)) {
            return VOID;
        }
        return ANY;
    }

    @Nonnull
    public GenericType fromPsiClass(@Nonnull PsiClass psiClass) {
        if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) {
            return INT;
        }
        if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) {
            return LONG;
        }
        if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) {
            return DOUBLE;
        }
        return ANY;
    }

    @Nonnull
    public GenericType fromPsiType(@Nonnull PsiType type) {
        if (PsiTypes.voidType().equals(type)) {
            return VOID;
        }
        if (PsiTypes.intType().equals(type)) {
            return INT;
        }
        if (PsiTypes.doubleType().equals(type)) {
            return DOUBLE;
        }
        if (PsiTypes.longType().equals(type)) {
            return LONG;
        }
        if (PsiTypes.booleanType().equals(type)) {
            return BOOLEAN;
        }
        return new ClassTypeImpl(TypeConversionUtil.erasure(type).getCanonicalText());
    }

    @Nonnull
    public GenericType unwrapOptional(@Nonnull GenericType type) {
        assert isOptional(type);

        if (type.equals(optionalInt)) {
            return INT;
        }
        if (type.equals(optionalLong)) {
            return LONG;
        }
        if (type.equals(optionalDouble)) {
            return DOUBLE;
        }
        return ANY;
    }

    private boolean isOptional(@Nonnull GenericType type) {
        return OPTIONAL_TYPES.contains(type);
    }
}
