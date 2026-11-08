// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiField;
import consulo.language.psi.PsiManager;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeParameter;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.language.psi.util.InheritanceUtil;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static com.intellij.java.language.psi.util.TypeConversionUtil.canConvertSealedTo;

public class JavaClassDef implements TypeConstraints.ClassDef {
  private final PsiClass myClass;

  public JavaClassDef(PsiClass aClass) {
    myClass = aClass;
  }

  @Override
  public boolean isInheritor(TypeConstraints.ClassDef superType) {
    return superType instanceof JavaClassDef && InheritanceUtil.isInheritorOrSelf(myClass, ((JavaClassDef)superType).myClass, true);
  }

  @Override
  public boolean isConvertible(TypeConstraints.ClassDef other) {
    if (!(other instanceof JavaClassDef)) return false;
    PsiClass otherClass = ((JavaClassDef)other).myClass;
    if (myClass.isInterface() || otherClass.isInterface()) {
      if (otherClass.hasModifierProperty(PsiModifier.SEALED)) return canConvertSealedTo(otherClass, myClass);
      if (myClass.hasModifierProperty(PsiModifier.SEALED)) return canConvertSealedTo(myClass, otherClass);
    }
    if (myClass.isInterface() && otherClass.isInterface()) return true;
    if (myClass.isInterface() && !otherClass.hasModifierProperty(PsiModifier.FINAL)) return true;
    if (otherClass.isInterface() && !myClass.hasModifierProperty(PsiModifier.FINAL)) return true;
    PsiManager manager = myClass.getManager();
    return manager.areElementsEquivalent(myClass, otherClass) ||
           otherClass.isInheritor(myClass, true) ||
           myClass.isInheritor(otherClass, true);
  }

  @Override
  public boolean isInterface() {
    return myClass.isInterface();
  }

  @Override
  public boolean isEnum() {
    return myClass.isEnum();
  }

  @Override
  public @Nullable PsiEnumConstant getEnumConstant(int ordinal) {
    int cur = 0;
    for (PsiField field : myClass.getFields()) {
      if (field instanceof PsiEnumConstant) {
        if (cur == ordinal) return (PsiEnumConstant)field;
        cur++;
      }
    }
    return null;
  }

  @Override
  public @Nullable String getQualifiedName() {
    return myClass.getQualifiedName();
  }

  @Override
  public StreamEx<TypeConstraints.ClassDef> superTypes() {
    Set<PsiClass> superTypes = new LinkedHashSet<>();
    InheritanceUtil.processSupers(myClass, false, t -> {
      if (!(t instanceof PsiTypeParameter) && !t.hasModifierProperty(PsiModifier.FINAL)) {
        superTypes.add(t);
      }
      return true;
    });
    return StreamEx.of(superTypes).map(JavaClassDef::new);
  }

  @Override
  public PsiType toPsiType(Project project) {
    return JavaPsiFacade.getElementFactory(project).createType(myClass);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof JavaClassDef &&
           myClass.getManager().areElementsEquivalent(myClass, ((JavaClassDef)obj).myClass);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myClass.getName());
  }

  @Override
  public boolean isFinal() {
    return myClass instanceof PsiAnonymousClass || myClass.hasModifierProperty(PsiModifier.FINAL);
  }

  @Override
  public boolean isAbstract() {
    return myClass.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  @Override
  public String toString() {
    String name = myClass.getQualifiedName();
    if (name == null) {
      name = myClass.getName();
    }
    if (name == null && myClass instanceof PsiAnonymousClass anonymousClass) {
      PsiClass baseClass = anonymousClass.getBaseClassType().resolve();
      name = "anonymous " +
             (baseClass == null ? anonymousClass.getBaseClassReference().getReferenceName() : new JavaClassDef(baseClass));
    }
    return String.valueOf(name);
  }

  public static TypeConstraints.TypeConstraintFactory typeConstraintFactory(PsiElement context) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
    GlobalSearchScope scope = context.getResolveScope();
    return fqn -> {
      PsiClass psiClass = facade.findClass(fqn, scope);
      return psiClass == null ? TypeConstraints.unresolved(fqn) : TypeConstraints.exactClass(psiClass);
    };
  }
}
