// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl;

import consulo.internal.com.sun.jdi.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

public class JvmValue implements consulo.execution.debug.stream.trace.Value {
    private final consulo.internal.com.sun.jdi.Value value;

    public JvmValue(@Nonnull consulo.internal.com.sun.jdi.Value value) {
        this.value = value;
    }

    @Nonnull
    public consulo.internal.com.sun.jdi.Value getValue() {
        return value;
    }

    @Nonnull
    @Override
    public String typeName() {
        return value.type().name();
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JvmValue jvmValue)) return false;
        return Objects.equals(value, jvmValue.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Nullable
    public static consulo.execution.debug.stream.trace.Value convertJvmValueToStreamValue(@Nullable consulo.internal.com.sun.jdi.Value jvmValue) {
        if (jvmValue == null) {
            return null;
        }
        if (jvmValue instanceof consulo.internal.com.sun.jdi.ArrayReference arrayRef) {
            return new JvmArrayReference(arrayRef);
        }
        if (!(jvmValue instanceof PrimitiveValue)) {
            return new JvmValue(jvmValue);
        }
        if (jvmValue instanceof DoubleValue doubleValue) {
            return new JvmDoubleValue(doubleValue);
        }
        if (jvmValue instanceof LongValue longValue) {
            return new JvmLongValue(longValue);
        }
        if (jvmValue instanceof ByteValue byteValue) {
            return new JvmByteValue(byteValue);
        }
        if (jvmValue instanceof CharValue charValue) {
            return new JvmCharValue(charValue);
        }
        if (jvmValue instanceof FloatValue floatValue) {
            return new JvmFloatValue(floatValue);
        }
        if (jvmValue instanceof BooleanValue booleanValue) {
            return new JvmBooleanValue(booleanValue);
        }
        if (jvmValue instanceof IntegerValue integerValue) {
            return new JvmIntegerValue(integerValue);
        }
        if (jvmValue instanceof ShortValue shortValue) {
            return new JvmShortValue(shortValue);
        }
        return new JvmPrimitiveValue((PrimitiveValue) jvmValue);
    }
}

class JvmArrayReference extends JvmValue implements consulo.execution.debug.stream.trace.ArrayReference {
    private final consulo.internal.com.sun.jdi.ArrayReference reference;

    JvmArrayReference(@Nonnull consulo.internal.com.sun.jdi.ArrayReference reference) {
        super(reference);
        this.reference = reference;
    }

    @Nullable
    @Override
    public consulo.execution.debug.stream.trace.Value getValue(int i) {
        return JvmValue.convertJvmValueToStreamValue(reference.getValue(i));
    }

    @Override
    public int length() {
        return reference.length();
    }
}

class JvmPrimitiveValue extends JvmValue {
    private final PrimitiveValue primitiveValue;

    JvmPrimitiveValue(@Nonnull PrimitiveValue value) {
        super(value);
        this.primitiveValue = value;
    }

    @Nonnull
    public PrimitiveValue getPrimitiveValue() {
        return primitiveValue;
    }
}

class JvmDoubleValue extends JvmPrimitiveValue implements consulo.execution.debug.stream.trace.DoubleValue {
    private final consulo.internal.com.sun.jdi.DoubleValue doubleValue;

    JvmDoubleValue(@Nonnull consulo.internal.com.sun.jdi.DoubleValue value) {
        super(value);
        this.doubleValue = value;
    }

    @Override
    public double value() {
        return doubleValue.value();
    }
}

class JvmLongValue extends JvmPrimitiveValue implements consulo.execution.debug.stream.trace.LongValue {
    private final consulo.internal.com.sun.jdi.LongValue longValue;

    JvmLongValue(@Nonnull consulo.internal.com.sun.jdi.LongValue value) {
        super(value);
        this.longValue = value;
    }

    @Override
    public long value() {
        return longValue.value();
    }
}

class JvmByteValue extends JvmPrimitiveValue implements consulo.execution.debug.stream.trace.ByteValue {
    private final consulo.internal.com.sun.jdi.ByteValue byteValue;

    JvmByteValue(@Nonnull consulo.internal.com.sun.jdi.ByteValue value) {
        super(value);
        this.byteValue = value;
    }

    @Override
    public byte value() {
        return byteValue.value();
    }
}

class JvmCharValue extends JvmPrimitiveValue implements consulo.execution.debug.stream.trace.CharValue {
    private final consulo.internal.com.sun.jdi.CharValue charValue;

    JvmCharValue(@Nonnull consulo.internal.com.sun.jdi.CharValue value) {
        super(value);
        this.charValue = value;
    }

    @Override
    public char value() {
        return charValue.value();
    }
}

class JvmFloatValue extends JvmPrimitiveValue implements consulo.execution.debug.stream.trace.FloatValue {
    private final consulo.internal.com.sun.jdi.FloatValue floatValue;

    JvmFloatValue(@Nonnull consulo.internal.com.sun.jdi.FloatValue value) {
        super(value);
        this.floatValue = value;
    }

    @Override
    public float value() {
        return floatValue.value();
    }
}

class JvmBooleanValue extends JvmPrimitiveValue implements consulo.execution.debug.stream.trace.BooleanValue {
    private final consulo.internal.com.sun.jdi.BooleanValue booleanValue;

    JvmBooleanValue(@Nonnull consulo.internal.com.sun.jdi.BooleanValue value) {
        super(value);
        this.booleanValue = value;
    }

    @Override
    public boolean value() {
        return booleanValue.value();
    }
}

class JvmIntegerValue extends JvmPrimitiveValue implements consulo.execution.debug.stream.trace.IntegerValue {
    private final consulo.internal.com.sun.jdi.IntegerValue integerValue;

    JvmIntegerValue(@Nonnull consulo.internal.com.sun.jdi.IntegerValue value) {
        super(value);
        this.integerValue = value;
    }

    @Override
    public int value() {
        return integerValue.value();
    }
}

class JvmShortValue extends JvmPrimitiveValue implements consulo.execution.debug.stream.trace.ShortValue {
    private final consulo.internal.com.sun.jdi.ShortValue shortValue;

    JvmShortValue(@Nonnull consulo.internal.com.sun.jdi.ShortValue value) {
        super(value);
        this.shortValue = value;
    }

    @Override
    public short value() {
        return shortValue.value();
    }
}
