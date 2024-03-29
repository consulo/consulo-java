// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.psi.PsiFile;
import consulo.util.io.DigestUtil;
import consulo.util.lang.ThreadLocalCachedValue;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.Direction.InOut;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.Direction.InThrow;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisConverter {
  private static final ThreadLocalCachedValue<MessageDigest> DIGEST_CACHE = new ThreadLocalCachedValue<MessageDigest>() {
    @Nonnull
    @Override
    public MessageDigest create() {
      return DigestUtil.md5();
    }

    @Override
    protected void init(@Nonnull MessageDigest value) {
      value.reset();
    }
  };

  public static MessageDigest getMessageDigest() {
    return DIGEST_CACHE.getValue();
  }

  /**
   * Creates a stable non-negated EKey for given PsiMethod and direction
   * Returns null if conversion is impossible (something is not resolvable).
   */
  @Nullable
  public static EKey psiKey(@Nonnull PsiMember psiMethod, @Nonnull Direction direction) {
    PsiClass psiClass = psiMethod.getContainingClass();
    if (psiClass != null) {
      String className = descriptor(psiClass, 0, false);
      String name = psiMethod.getName();
      String sig;
      if (psiMethod instanceof PsiMethod) {
        sig = methodSignature((PsiMethod) psiMethod, psiClass);
        if (((PsiMethod) psiMethod).isConstructor()) {
          name = "<init>";
        }
      } else if (psiMethod instanceof PsiField) {
        sig = descriptor(((PsiField) psiMethod).getType());
      } else {
        return null;
      }
      if (className != null && sig != null && name != null) {
        return new EKey(new Member(className, name, sig), direction, true, false);
      }
    }
    return null;
  }

  @Nullable
  private static String methodSignature(@Nonnull PsiMethod psiMethod, @Nonnull PsiClass psiClass) {
    StringBuilder sb = new StringBuilder();

    sb.append('(');
    if (psiMethod.isConstructor() && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass outerClass = psiClass.getContainingClass();
      if (outerClass != null) {
        String desc = descriptor(outerClass, 0, true);
        if (desc == null) {
          return null;
        }
        sb.append(desc);
      }
    }
    for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
      String desc = descriptor(parameter.getType());
      if (desc == null) {
        return null;
      }
      sb.append(desc);
    }
    sb.append(')');

    PsiType returnType = psiMethod.getReturnType();
    if (returnType == null) {
      sb.append('V');
    } else {
      String desc = descriptor(returnType);
      if (desc == null) {
        return null;
      }
      sb.append(desc);
    }

    return sb.toString();
  }

  @Nullable
  private static String descriptor(@Nonnull PsiClass psiClass, int dimensions, boolean full) {
    PsiFile containingFile = psiClass.getContainingFile();
    if (!(containingFile instanceof PsiClassOwner)) {
      LOG.debug("containingFile was not resolved for " + psiClass.getQualifiedName());
      return null;
    }
    PsiClassOwner psiFile = (PsiClassOwner) containingFile;
    String packageName = psiFile.getPackageName();
    String qname = psiClass.getQualifiedName();
    if (qname == null) {
      return null;
    }
    String className;
    if (packageName.length() > 0) {
      className = qname.substring(packageName.length() + 1).replace('.', '$');
    } else {
      className = qname.replace('.', '$');
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < dimensions; i++) {
      sb.append('[');
    }
    if (full) {
      sb.append('L');
    }
    if (packageName.length() > 0) {
      sb.append(packageName.replace('.', '/'));
      sb.append('/');
    }
    sb.append(className);
    if (full) {
      sb.append(';');
    }
    return sb.toString();
  }

  @Nullable
  private static String descriptor(@Nonnull PsiType psiType) {
    int dimensions = 0;
    psiType = TypeConversionUtil.erasure(psiType);
    if (psiType instanceof PsiArrayType) {
      PsiArrayType arrayType = (PsiArrayType) psiType;
      psiType = arrayType.getDeepComponentType();
      dimensions = arrayType.getArrayDimensions();
    }

    if (psiType instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType) psiType).resolve();
      if (psiClass != null) {
        return descriptor(psiClass, dimensions, true);
      } else {
        LOG.debug("resolve was null for " + psiType.getCanonicalText());
        return null;
      }
    } else if (psiType instanceof PsiPrimitiveType) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < dimensions; i++) {
        sb.append('[');
      }
      if (PsiType.VOID.equals(psiType)) {
        sb.append('V');
      } else if (PsiType.BOOLEAN.equals(psiType)) {
        sb.append('Z');
      } else if (PsiType.CHAR.equals(psiType)) {
        sb.append('C');
      } else if (PsiType.BYTE.equals(psiType)) {
        sb.append('B');
      } else if (PsiType.SHORT.equals(psiType)) {
        sb.append('S');
      } else if (PsiType.INT.equals(psiType)) {
        sb.append('I');
      } else if (PsiType.FLOAT.equals(psiType)) {
        sb.append('F');
      } else if (PsiType.LONG.equals(psiType)) {
        sb.append('J');
      } else if (PsiType.DOUBLE.equals(psiType)) {
        sb.append('D');
      }
      return sb.toString();
    }
    return null;
  }


  /**
   * Given a PSI method and its primary Key enumerate all contract keys for it.
   *
   * @param psiMethod  psi method
   * @param primaryKey primary stable keys
   * @return corresponding (stable!) keys
   */
  @Nonnull
  public static ArrayList<EKey> mkInOutKeys(@Nonnull PsiMethod psiMethod, @Nonnull EKey primaryKey) {
    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    ArrayList<EKey> keys = new ArrayList<>(parameters.length * 2 + 2);
    keys.add(primaryKey);
    for (int i = 0; i < parameters.length; i++) {
      if (!(parameters[i].getType() instanceof PsiPrimitiveType)) {
        keys.add(primaryKey.withDirection(new InOut(i, Value.NotNull)));
        keys.add(primaryKey.withDirection(new InOut(i, Value.Null)));
        keys.add(primaryKey.withDirection(new InThrow(i, Value.NotNull)));
        keys.add(primaryKey.withDirection(new InThrow(i, Value.Null)));
      } else if (PsiType.BOOLEAN.equals(parameters[i].getType())) {
        keys.add(primaryKey.withDirection(new InOut(i, Value.True)));
        keys.add(primaryKey.withDirection(new InOut(i, Value.False)));
        keys.add(primaryKey.withDirection(new InThrow(i, Value.True)));
        keys.add(primaryKey.withDirection(new InThrow(i, Value.False)));
      }
    }
    return keys;
  }

  public static void addEffectAnnotations(Map<EKey, Effects> puritySolutions,
                                          MethodAnnotations result,
                                          EKey methodKey,
                                          boolean constructor) {
    for (Map.Entry<EKey, Effects> entry : puritySolutions.entrySet()) {
      EKey key = entry.getKey().mkStable();
      EKey baseKey = key.mkBase();
      if (!methodKey.equals(baseKey)) {
        continue;
      }
      result.returnValue = entry.getValue().returnValue;
      Set<EffectQuantum> effects = entry.getValue().effects;
      if (effects.isEmpty() || (constructor && effects.size() == 1 && effects.contains(EffectQuantum.ThisChangeQuantum))) {
        // Pure constructor is allowed to change "this" object as this is a new object anyways
        result.pures.add(methodKey);
      }
    }
  }
}