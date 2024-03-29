// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import consulo.internal.org.objectweb.asm.tree.FieldInsnNode;
import consulo.util.lang.Couple;
import consulo.util.lang.StringUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class HardCodedPurity {
  static final boolean AGGRESSIVE_HARDCODED_PURITY = true;

  private static final Set<Couple<String>> ownedFields = Collections.singleton(
      new Couple<>("java/lang/AbstractStringBuilder", "value")
  );
  private static final Set<Member> thisChangingMethods = Set.of(
      new Member("java/lang/Throwable", "fillInStackTrace", "()Ljava/lang/Throwable;")
  );
  // Assumed that all these methods are not only pure, but return object which could be safely modified
  private static final Set<Member> pureMethods = Set.of(
      // Maybe overloaded and be not pure, but this would be definitely bad code style
      // Used in Throwable(Throwable) ctor, so this helps to infer purity of many exception constructors
      new Member("java/lang/Throwable", "toString", "()Ljava/lang/String;"),
      // Cycle in AbstractStringBuilder ctor and this method disallows to infer the purity
      new Member("java/lang/StringUTF16", "newBytesFor", "(I)[B"),
      // Declared in final class StringBuilder
      new Member("java/lang/StringBuilder", "toString", "()Ljava/lang/String;"),
      new Member("java/lang/StringBuffer", "toString", "()Ljava/lang/String;"),
      // Often used in generated code since Java 9; to avoid too many equations
      new Member("java/util/Objects", "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;"),
      // Native
      new Member("java/lang/Object", "getClass", "()Ljava/lang/Class;"),
      new Member("java/lang/Class", "getComponentType", "()Ljava/lang/Class;"),
      new Member("java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;I)Ljava/lang/Object;"),
      new Member("java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;[I)Ljava/lang/Object;"),
      new Member("java/lang/Float", "floatToRawIntBits", "(F)I"),
      new Member("java/lang/Float", "intBitsToFloat", "(I)F"),
      new Member("java/lang/Double", "doubleToRawLongBits", "(D)J"),
      new Member("java/lang/Double", "longBitsToDouble", "(J)D")
  );
  private static final Map<Member, Set<EffectQuantum>> solutions = new HashMap<>();
  private static final Set<EffectQuantum> thisChange = Collections.singleton(EffectQuantum.ThisChangeQuantum);

  static {
    // Native
    solutions.put(new Member("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V"),
        Collections.singleton(new EffectQuantum.ParamChangeQuantum(2)));
    solutions.put(new Member("java/lang/Object", "hashCode", "()I"), Collections.emptySet());
  }

  static HardCodedPurity getInstance() {
    return AGGRESSIVE_HARDCODED_PURITY ? new AggressiveHardCodedPurity() : new HardCodedPurity();
  }

  Effects getHardCodedSolution(Member method) {
    if (isThisChangingMethod(method)) {
      return new Effects(isBuilderChainCall(method) ? DataValue.ThisDataValue : DataValue.UnknownDataValue1, thisChange);
    } else if (isPureMethod(method)) {
      return new Effects(getReturnValueForPureMethod(method), Collections.emptySet());
    } else {
      Set<EffectQuantum> effects = solutions.get(method);
      return effects == null ? null : new Effects(DataValue.UnknownDataValue1, effects);
    }
  }

  boolean isThisChangingMethod(Member method) {
    return isBuilderChainCall(method) || thisChangingMethods.contains(method);
  }

  boolean isBuilderChainCall(Member method) {
    // Those methods are virtual, thus contracts cannot be inferred automatically,
    // but all possible implementations are controlled
    // (only final classes j.l.StringBuilder and j.l.StringBuffer extend package-private j.l.AbstractStringBuilder)
    return (method.internalClassName.equals("java/lang/StringBuilder") || method.internalClassName.equals("java/lang/StringBuffer")) &&
        method.methodName.startsWith("append");
  }

  DataValue getReturnValueForPureMethod(Member method) {
    String type = StringUtil.substringAfter(method.methodDesc, ")");
    if (type != null && (type.length() == 1 || type.equals("Ljava/lang/String;") || type.equals("Ljava/lang/Class;"))) {
      return DataValue.UnknownDataValue1;
    }
    return DataValue.LocalDataValue;
  }

  boolean isPureMethod(Member method) {
    if (pureMethods.contains(method)) {
      return true;
    }
    // Array clone() method is a special beast: it's qualifier class is array itself
    if (method.internalClassName.startsWith("[") && method.methodName.equals("clone") && method.methodDesc.equals("()Ljava/lang/Object;")) {
      return true;
    }
    return false;
  }

  boolean isOwnedField(FieldInsnNode fieldInsn) {
    return ownedFields.contains(new Couple<>(fieldInsn.owner, fieldInsn.name));
  }

  static class AggressiveHardCodedPurity extends HardCodedPurity {
    static final Set<String> ITERABLES = Set.of("java/lang/Iterable", "java/util/Collection",
        "java/util/List", "java/util/Set", "java/util/ArrayList",
        "java/util/HashSet", "java/util/AbstractList",
        "java/util/AbstractSet", "java/util/TreeSet");

    @Override
    boolean isThisChangingMethod(Member method) {
      if (method.methodName.equals("next") && method.methodDesc.startsWith("()") && method.internalClassName.equals("java/util/Iterator")) {
        return true;
      }
      return super.isThisChangingMethod(method);
    }

    @Override
    boolean isPureMethod(Member method) {
      if (method.methodName.equals("toString") && method.methodDesc.equals("()Ljava/lang/String;")) {
        return true;
      }
      if (method.methodName.equals("iterator") && method.methodDesc.equals("()Ljava/util/Iterator;") &&
          ITERABLES.contains(method.internalClassName)) {
        return true;
      }
      if (method.methodName.equals("hasNext") && method.methodDesc.equals("()Z") && method.internalClassName.equals("java/util/Iterator")) {
        return true;
      }
      return super.isPureMethod(method);
    }
  }
}