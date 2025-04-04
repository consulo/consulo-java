// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Represents method mutation signature
 */
public final class MutationSignature {
    public static final String ATTR_MUTATES = "mutates";
    static final MutationSignature UNKNOWN = new MutationSignature(false, new boolean[0]);
    static final MutationSignature PURE = new MutationSignature(false, new boolean[0]);
    private static final MutationSignature MUTATES_THIS_ONLY = new MutationSignature(true, new boolean[0]);
    private final boolean myThis;
    private final boolean[] myParameters;

    private MutationSignature(boolean mutatesThis, boolean[] params) {
        myThis = mutatesThis;
        myParameters = params;
    }

    /**
     * @return true if the instance method may mutate this object
     */
    public boolean mutatesThis() {
        return myThis;
    }

    /**
     * @param n argument number (zero-based)
     * @return true if the method may mutate given argument
     */
    public boolean mutatesArg(int n) {
        return n < myParameters.length && myParameters[n];
    }

    /**
     * @return true if the method is static or never mutates this object
     */
    public boolean preservesThis() {
        return this != UNKNOWN && !myThis;
    }

    /**
     * @param n argument number (zero-based)
     * @return true if the method never mutates given argument
     */
    public boolean preservesArg(int n) {
        return this != UNKNOWN && !mutatesArg(n);
    }

    /**
     * @return a signature that is equivalent to this signature but may also mutate this object
     */
    public MutationSignature alsoMutatesThis() {
        return this == UNKNOWN || myThis ? this :
            isPure() ? MUTATES_THIS_ONLY : new MutationSignature(true, myParameters);
    }

    /**
     * @param n argument number (zero-based)
     * @return a signature that is equivalent to this signature but may also mutate n-th argument
     */
    public MutationSignature alsoMutatesArg(int n) {
        if (myParameters.length > n && myParameters[n]) {
            return this;
        }
        boolean[] params = Arrays.copyOf(myParameters, Math.max(n + 1, myParameters.length));
        params[n] = true;
        return new MutationSignature(myThis, params);
    }

    /**
     * @return true if this signature represents a pure method
     */
    public boolean isPure() {
        return this == PURE;
    }

    @Override
    public int hashCode() {
        return (myThis ? 137 : 731) + Arrays.hashCode(myParameters);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if ((this == UNKNOWN) != (obj == UNKNOWN)) {
            return false;
        }
        return obj instanceof MutationSignature mutationSignature
            && mutationSignature.myThis == myThis
            && Arrays.equals(mutationSignature.myParameters, myParameters);
    }

    @Override
    public String toString() {
        if (isPure()) {
            return "(pure)";
        }
        if (this == UNKNOWN) {
            return "(unknown)";
        }
        return IntStreamEx.range(myParameters.length).mapToEntry(idx -> "param" + (idx + 1), idx -> myParameters[idx])
            .prepend("this", myThis).filterValues(b -> b).keys().joining(",");
    }

    /**
     * Returns a stream of expressions which are mutated by given signature assuming that supplied call
     * resolves to the method with this signature.
     *
     * @param call a call which resolves to the method with this mutation signature
     * @return a stream of expressions which are mutated by this call. If qualifier is omitted, but mutated,
     * a non-physical {@link PsiThisExpression} might be returned.
     */
    @RequiredReadAction
    public Stream<PsiExpression> mutatedExpressions(PsiMethodCallExpression call) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        StreamEx<PsiExpression> elements =
            IntStreamEx.range(Math.min(myParameters.length, MethodCallUtils.isVarArgCall(call) ? args.length - 1 : args.length))
                .filter(idx -> myParameters[idx]).elements(args);
        if (myThis) {
            PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
            if (qualifier != null) {
                return elements.prepend(qualifier);
            }
        }
        return elements;
    }

    /**
     * @return true if known to mutate any parameter or receiver; false if pure or not known
     */
    public boolean mutatesAnything() {
        if (myThis) {
            return true;
        }
        for (boolean parameter : myParameters) {
            if (parameter) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param signature to parse
     * @return a parsed mutation signature
     * @throws IllegalArgumentException if signature is invalid
     */
    public static MutationSignature parse(@Nonnull String signature) {
        if (signature.trim().isEmpty()) {
            return UNKNOWN;
        }
        boolean mutatesThis = false;
        boolean[] args = {};
        for (String part : signature.split(",")) {
            part = part.trim();
            if (part.equals("this")) {
                mutatesThis = true;
            }
            else if (part.equals("param")) {
                if (args.length == 0) {
                    args = new boolean[]{true};
                }
                else {
                    args[0] = true;
                }
            }
            else if (part.startsWith("param")) {
                int argNum = Integer.parseInt(part.substring("param".length()));
                if (argNum < 0 || argNum > 255) {
                    throw new IllegalArgumentException(JavaAnalysisLocalize.mutationSignatureProblemInvalidToken(part).get());
                }
                if (args.length < argNum) {
                    args = Arrays.copyOf(args, argNum);
                }
                args[argNum - 1] = true;
            }
            else if (!part.isEmpty()) {
                throw new IllegalArgumentException(JavaAnalysisLocalize.mutationSignatureProblemInvalidToken(part).get());
            }
        }
        return new MutationSignature(mutatesThis, args);
    }

    /**
     * Checks the mutation signature
     *
     * @param signature signature to check
     * @param method    a method to apply the signature
     * @return error message or null if signature is valid
     */
    @Nullable
    public static String checkSignature(@Nonnull String signature, @Nonnull PsiMethod method) {
        try {
            MutationSignature ms = parse(signature);
            if (ms.myThis && method.hasModifierProperty(PsiModifier.STATIC)) {
                return JavaAnalysisLocalize.mutationSignatureProblemStaticMethodCannotMutateThis().get();
            }
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (ms.myParameters.length > parameters.length) {
                return JavaAnalysisLocalize.mutationSignatureProblemReferenceToParameterInvalid(ms.myParameters.length).get();
            }
            for (int i = 0; i < ms.myParameters.length; i++) {
                if (ms.myParameters[i]) {
                    PsiType type = parameters[i].getType();
                    if (ClassUtils.isImmutable(type)) {
                        return JavaAnalysisLocalize.mutationSignatureProblemParameterHasImmutableType(i + 1, type.getPresentableText())
                            .get();
                    }
                }
            }
        }
        catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        return null;
    }

    @Nonnull
    public static MutationSignature fromMethod(@Nullable PsiMethod method) {
        if (method == null) {
            return UNKNOWN;
        }
        return JavaMethodContractUtil.getContractInfo(method).getMutationSignature();
    }

    @Nonnull
    @RequiredReadAction
    public static MutationSignature fromCall(@Nullable PsiCall call) {
        if (call == null) {
            return UNKNOWN;
        }
        PsiMethod method = call.resolveMethod();
        if (method != null) {
            if (SpecialField.findSpecialField(method) != null) {
                return PURE;
            }
            return fromMethod(method);
        }
        if (call instanceof PsiNewExpression) {
            PsiNewExpression newExpression = (PsiNewExpression)call;
            if (newExpression.isArrayCreation()) {
                return PURE;
            }
            if (newExpression.getArgumentList() == null || !newExpression.getArgumentList().isEmpty()) {
                return UNKNOWN;
            }
            PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
            if (classReference == null) {
                return UNKNOWN;
            }
            PsiClass clazz = ObjectUtil.tryCast(classReference.resolve(), PsiClass.class);
            if (clazz == null) {
                return UNKNOWN;
            }
            Set<PsiClass> visited = new HashSet<>();
            while (true) {
                for (PsiField field : clazz.getFields()) {
                    if (!field.hasModifierProperty(PsiModifier.STATIC) && field.hasInitializer()) {
                        return UNKNOWN;
                    }
                }
                for (PsiClassInitializer initializer : clazz.getInitializers()) {
                    if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
                        return UNKNOWN;
                    }
                }
                for (PsiMethod ctor : clazz.getConstructors()) {
                    if (ctor.getParameterList().isEmpty()) {
                        return fromMethod(ctor);
                    }
                }
                clazz = clazz.getSuperClass();
                if (clazz == null || !visited.add(clazz)) {
                    return unknown();
                }
            }
        }
        return UNKNOWN;
    }

    /**
     * @return a signature of the pure method, which doesn't mutate anything
     */
    @Nonnull
    public static MutationSignature pure() {
        return PURE;
    }

    /**
     * @return a signature of the unknown method, which may mutate anything
     */
    @Nonnull
    public static MutationSignature unknown() {
        return UNKNOWN;
    }
}
