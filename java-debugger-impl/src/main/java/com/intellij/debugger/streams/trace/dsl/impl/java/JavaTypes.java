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

    @Override
    public GenericType ANY() {
        return ANY;
    }

    @Override
    public GenericType INT() {
        return INT;
    }

    @Override
    public GenericType BOOLEAN() {
        return BOOLEAN;
    }

    @Override
    public GenericType DOUBLE() {
        return DOUBLE;
    }

    @Override
    public GenericType EXCEPTION() {
        return EXCEPTION;
    }

    @Override
    public GenericType VOID() {
        return VOID;
    }

    @Override
    public GenericType TIME() {
        return TIME;
    }

    @Override
    public GenericType STRING() {
        return STRING;
    }

    @Override
    public GenericType LONG() {
        return LONG;
    }

    @Override
    public ArrayType array(GenericType elementType) {
        return new ArrayTypeImpl(elementType,
                                 name -> name + "[]",
                                 size -> "new " + elementType.getVariableTypeName() + "[" + size + "]");
    }

    @Override
    public MapType map(GenericType keyType, GenericType valueType) {
        return new MapTypeImpl(keyType, valueType,
                               (keys, values) -> "java.util.Map<" + keys + ", " + values + ">",
                               "new java.util.HashMap<>()",
                               (keys, values) -> "java.util.Map.Entry<" + keys + ", " + values + ">");
    }

    @Override
    public MapType linkedMap(GenericType keyType, GenericType valueType) {
        return new MapTypeImpl(keyType, valueType,
                               (keys, values) -> "java.util.Map<" + keys + ", " + values + ">",
                               "new java.util.LinkedHashMap<>()",
                               (keys, values) -> "java.util.Map.Entry<" + keys + ", " + values + ">");
    }

    @Override
    public ListType list(GenericType elementsType) {
        return new ListTypeImpl(elementsType,
                                type -> "java.util.List<" + type + ">",
                                "new java.util.ArrayList<>()");
    }

    @Override
    public GenericType nullable(Function<Types, GenericType> typeSelector) {
        return typeSelector.apply(this);
    }

    public GenericType fromStreamPsiType(PsiType streamPsiType) {
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

    public GenericType fromPsiClass(PsiClass psiClass) {
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

    public GenericType fromPsiType(PsiType type) {
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

    public GenericType unwrapOptional(GenericType type) {
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

    private boolean isOptional(GenericType type) {
        return OPTIONAL_TYPES.contains(type);
    }
}
