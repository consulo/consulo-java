// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.TypeNullability;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.JavaTypeNullabilityUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public class PsiImmediateClassType extends PsiClassType.Stub {
    private final PsiClass myClass;
    private final PsiSubstitutor mySubstitutor;
    private final PsiManager myManager;
    private final @Nullable PsiElement myPsiContext;
    private @Nullable TypeNullability myNullability;
    private String myCanonicalText;
    private String myCanonicalTextAnnotated;
    private String myPresentableText;
    private String myPresentableTextAnnotated;
    private String myInternalCanonicalText;
    private String myClassName;

    private final ClassResolveResult myClassResolveResult = new ClassResolveResult() {
        private ClassResolveResult myCapturedResult = null;

        @Override
        public PsiClass getElement() {
            return myClass;
        }

        @Override
        public PsiSubstitutor getSubstitutor() {
            return mySubstitutor;
        }

        @Override
        public ClassResolveResult resolveWithCapturedTopLevelWildcards() {
            ClassResolveResult result = myCapturedResult;
            if (result == null) {
                myCapturedResult = result = ClassResolveResult.super.resolveWithCapturedTopLevelWildcards();
            }
            return result;
        }

        @Override
        public boolean isValidResult() {
            return true;
        }

        @Override
        public boolean isAccessible() {
            return true;
        }

        @Override
        public boolean isStaticsScopeCorrect() {
            return true;
        }

        @Override
        public PsiElement getCurrentFileResolveScope() {
            return null;
        }

        @Override
        public boolean isPackagePrefixPackageReference() {
            return false;
        }
    };

    public PsiImmediateClassType(PsiClass aClass, PsiSubstitutor substitutor) {
        this(aClass, substitutor, null, TypeAnnotationProvider.EMPTY);
    }

    public PsiImmediateClassType(PsiClass aClass, PsiSubstitutor substitutor, @Nullable LanguageLevel level) {
        this(aClass, substitutor, level, TypeAnnotationProvider.EMPTY);
    }

    public PsiImmediateClassType(PsiClass aClass,
                                 PsiSubstitutor substitutor,
                                 @Nullable LanguageLevel level,
                                 PsiAnnotation ... annotations) {
        this(aClass, substitutor, level, TypeAnnotationProvider.Static.create(annotations));
    }

    public PsiImmediateClassType(PsiClass aClass,
                                 PsiSubstitutor substitutor,
                                 @Nullable LanguageLevel level,
                                 TypeAnnotationProvider provider) {
        this(aClass, substitutor, level, provider, null);
    }

    public PsiImmediateClassType(PsiClass aClass,
                                 PsiSubstitutor substitutor,
                                 @Nullable LanguageLevel level,
                                 TypeAnnotationProvider provider,
                                 @Nullable PsiElement context) {
        this(aClass, substitutor, level, provider, context, null);
    }

    PsiImmediateClassType(PsiClass aClass,
                          PsiSubstitutor substitutor,
                          @Nullable LanguageLevel level,
                          TypeAnnotationProvider provider,
                          @Nullable PsiElement context,
                          @Nullable TypeNullability nullability) {
        super(level, provider);
        myClass = aClass;
        myManager = aClass.getManager();
        mySubstitutor = substitutor;
        myPsiContext = context;
        myNullability = nullability;
        substitutor.ensureValid();
    }

    @Override
    public PsiClass resolve() {
        return myClass;
    }

    @Override
    public String getClassName() {
        String className = myClassName;
        if (className == null) {
            myClassName = className = myClass.getName();
        }
        return className;
    }

    @Override
    public @Nullable PsiElement getPsiContext() {
        return myPsiContext;
    }

    @Override
    public TypeNullability getNullability() {
        TypeNullability nullability = myNullability;
        if (nullability == null) {
            myNullability = nullability = JavaTypeNullabilityUtil.getTypeNullability(this);
        }
        return nullability;
    }

    @Override
    public PsiClassType withNullability(TypeNullability nullability) {
        return new PsiImmediateClassType(myClass, mySubstitutor, myLanguageLevel, getAnnotationProvider(), myPsiContext, nullability);
    }

    @Override
    public PsiImmediateClassType annotate(TypeAnnotationProvider provider) {
        PsiImmediateClassType annotated = (PsiImmediateClassType) super.annotate(provider);
        if (annotated != this) {
            annotated.myNullability = null;
        }
        return annotated;
    }

    @Override
    public int getParameterCount() {
        PsiTypeParameterList list = myClass.getTypeParameterList();
        if (list == null) return 0;
        PsiTypeParameter[] parameters = list.getTypeParameters();
        if (mySubstitutor.hasRawSubstitution()) {
            for (PsiTypeParameter parameter : parameters) {
                if (mySubstitutor.substitute(parameter) == null) return 0;
            }
        }
        return parameters.length;
    }

    @Override
    public PsiType[] getParameters() {
        PsiTypeParameter[] parameters = myClass.getTypeParameters();
        if (parameters.length == 0) {
            return PsiType.EMPTY_ARRAY;
        }

        PsiType[] result = new PsiType[parameters.length];
        int pos = 0;
        for (PsiTypeParameter parameter : parameters) {
            PsiType substituted = mySubstitutor.substitute(parameter);
            if (substituted == null) {
                return PsiType.EMPTY_ARRAY;
            }
            result[pos++] = substituted;
        }
        assert pos == result.length;
        return result;
    }

    @Override
    public ClassResolveResult resolveGenerics() {
        return myClassResolveResult;
    }

    @Override
    public PsiClassType rawType() {
        return JavaPsiFacade.getElementFactory(myClass.getProject()).createType(myClass);
    }

    @Override
    public String getPresentableText(boolean annotated) {
        String presentableText;
        if (annotated) {
            presentableText = myPresentableTextAnnotated;
            if (presentableText == null) {
                return myPresentableTextAnnotated = getText(TextType.PRESENTABLE, true);
            }
        }
        else {
            presentableText = myPresentableText;
            if (presentableText == null) {
                return myPresentableText = getText(TextType.PRESENTABLE, false);
            }
        }
        return presentableText;
    }

    @Override
    public String getCanonicalText(boolean annotated) {
        String cached = annotated ? myCanonicalTextAnnotated : myCanonicalText;
        if (cached == null) {
            cached = getText(TextType.CANONICAL, annotated);
            if (annotated) myCanonicalTextAnnotated = cached;
            else myCanonicalText = cached;
        }
        return cached;
    }

    @Override
    public String getInternalCanonicalText() {
        String canonicalText = myInternalCanonicalText;
        if (canonicalText == null) {
            myInternalCanonicalText = canonicalText = getText(TextType.INT_CANONICAL, true);
        }
        return canonicalText;
    }

    private enum TextType {
        PRESENTABLE,
        CANONICAL,
        INT_CANONICAL
    }

    private String getText(TextType textType, boolean annotated) {
        mySubstitutor.ensureValid();
        StringBuilder buffer = new StringBuilder();
        buildText(myClass, mySubstitutor, buffer, textType, annotated);
        return buffer.toString();
    }

    private void buildText(PsiClass aClass,
                           PsiSubstitutor substitutor,
                           StringBuilder buffer,
                           TextType textType,
                           boolean annotated) {
        if (aClass instanceof PsiAnonymousClass) {
            ClassResolveResult baseResolveResult = ((PsiAnonymousClass) aClass).getBaseClassType().resolveGenerics();
            PsiClass baseClass = baseResolveResult.getElement();
            if (textType == TextType.INT_CANONICAL) {
                buffer.append("anonymous ");
            }
            if (baseClass != null) {
                buildText(baseClass, baseResolveResult.getSubstitutor(), buffer, textType, false);
            }
            else {
                buffer.append(((PsiAnonymousClass) aClass).getBaseClassReference().getCanonicalText());
            }
            return;
        }

        boolean qualified = textType != TextType.PRESENTABLE;

        PsiClass enclosingClass = null;
        if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
            PsiElement parent = aClass.getParent();
            if (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass) && !(parent instanceof PsiImplicitClass)) {
                enclosingClass = (PsiClass) parent;
            }
        }
        if (enclosingClass != null) {
            buildText(enclosingClass, substitutor, buffer, textType, false);
            buffer.append('.');
        }
        else if (qualified) {
            String fqn = aClass.getQualifiedName();
            if (fqn != null) {
                String prefix = StringUtil.getPackageName(fqn);
                if (!StringUtil.isEmpty(prefix)) {
                    buffer.append(prefix);
                    buffer.append('.');
                }
            }
        }

        if (annotated) {
            PsiNameHelper.appendAnnotations(buffer, getAnnotations(), qualified);
        }

        buffer.append(aClass.getName());

        PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
        if (typeParameters.length > 0) {
            int pos = buffer.length();
            buffer.append('<');

            for (int i = 0; i < typeParameters.length; i++) {
                PsiTypeParameter typeParameter = typeParameters[i];
                PsiUtilCore.ensureValid(typeParameter);

                if (i > 0) {
                    buffer.append(',');
                    if (textType == TextType.PRESENTABLE) buffer.append(' ');
                }

                PsiType substitutionResult = substitutor.substitute(typeParameter);
                if (substitutionResult == null) {
                    buffer.setLength(pos);
                    pos = -1;
                    break;
                }
                PsiUtil.ensureValidType(substitutionResult);

                if (textType == TextType.PRESENTABLE) {
                    buffer.append(substitutionResult.getPresentableText());
                }
                else if (textType == TextType.CANONICAL) {
                    buffer.append(substitutionResult.getCanonicalText(annotated));
                }
                else {
                    buffer.append(substitutionResult.getInternalCanonicalText());
                }
            }

            if (pos >= 0) {
                buffer.append('>');
            }
        }
    }

    @Override
    public boolean isValid() {
        for (PsiAnnotation annotation : getAnnotations()) {
            if (!annotation.isValid()) return false;
        }
        return myClass.isValid() && mySubstitutor.isValid();
    }

    @Override
    public boolean equalsToText(String text) {
        String name = myClass.getName();
        if (name == null || !text.contains(name)) return false;
        if (text.equals(getCanonicalText(false))) return true;

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(myManager.getProject());
        PsiType patternType;
        try {
            patternType = factory.createTypeFromText(text, myClass);
        }
        catch (IncorrectOperationException e) {
            return false;
        }
        return equals(patternType);
    }

    @Override
    public GlobalSearchScope getResolveScope() {
        return myClass.getResolveScope();
    }

    @Override
    public LanguageLevel getLanguageLevel() {
        return myLanguageLevel != null ? myLanguageLevel : PsiUtil.getLanguageLevel(myClass);
    }

    @Override
    public PsiClassType setLanguageLevel(LanguageLevel level) {
        return level.equals(myLanguageLevel) ? this : new PsiImmediateClassType(myClass, mySubstitutor, level, getAnnotationProvider(), null);
    }
}