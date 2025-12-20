/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.impl.refactoring.util;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author dsl
 */
public class CanonicalTypes {
  private static final Logger LOG = Logger.getInstance(CanonicalTypes.class);

  private CanonicalTypes() { }

  public abstract static class Type {
    @Nonnull
    public abstract PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException;

    @NonNls
    public abstract String getTypeText();

    public abstract void addImportsTo(JavaCodeFragment codeFragment);

    public boolean isValid() {
      return true;
    }
  }

  private static class Primitive extends Type {
    private final PsiPrimitiveType myType;

    private Primitive(PsiPrimitiveType type) {
      myType = type;
    }

    @Nonnull
    public PsiType getType(PsiElement context, PsiManager manager) {
      return myType;
    }

    public String getTypeText() {
      return myType.getPresentableText();
    }

    public void addImportsTo(JavaCodeFragment codeFragment) {}
  }

  private static class Array extends Type {
    private final Type myComponentType;

    private Array(Type componentType) {
      myComponentType = componentType;
    }

    @Nonnull
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      return myComponentType.getType(context, manager).createArrayType();
    }

    public String getTypeText() {
      return myComponentType.getTypeText() + "[]";
    }

    public void addImportsTo(JavaCodeFragment codeFragment) {
      myComponentType.addImportsTo(codeFragment);
    }

    @Override
    public boolean isValid() {
      return myComponentType.isValid();
    }
  }

  private static class Ellipsis extends Type {
    private final Type myComponentType;

    private Ellipsis(Type componentType) {
      myComponentType = componentType;
    }

    @Nonnull
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      return new PsiEllipsisType(myComponentType.getType(context, manager));
    }

    public String getTypeText() {
      return myComponentType.getTypeText() + "...";
    }

    public void addImportsTo(JavaCodeFragment codeFragment) {
      myComponentType.addImportsTo(codeFragment);
    }

    @Override
    public boolean isValid() {
      return myComponentType.isValid();
    }
  }

  private static class WildcardType extends Type {
    private final boolean myIsExtending;
    private final Type myBound;

    private WildcardType(boolean isExtending, Type bound) {
      myIsExtending = isExtending;
      myBound = bound;
    }

    @Nonnull
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      if(myBound == null) return PsiWildcardType.createUnbounded(context.getManager());
      if (myIsExtending) {
        return PsiWildcardType.createExtends(context.getManager(), myBound.getType(context, manager));
      }
      else {
        return PsiWildcardType.createSuper(context.getManager(), myBound.getType(context, manager));
      }
    }

    public String getTypeText() {
      if (myBound == null) return "?";
      return "? " + (myIsExtending ? "extends " : "super ") + myBound.getTypeText();
    }

    public void addImportsTo(JavaCodeFragment codeFragment) {
      if (myBound != null) myBound.addImportsTo(codeFragment);
    }

    @Override
    public boolean isValid() {
      return myBound == null || myBound.isValid();
    }
  }

  private static class WrongType extends Type {
    private final String myText;

    private WrongType(String text) {
      myText = text;
    }

    @Nonnull
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      return JavaPsiFacade.getInstance(context.getProject()).getElementFactory().createTypeFromText(myText, context);
    }

    public String getTypeText() {
      return myText;
    }

    public void addImportsTo(JavaCodeFragment codeFragment) {}

    @Override
    public boolean isValid() {
      return false;
    }
  }

  private static class ClassType extends Type {
    private final String myOriginalText;
    private final String myClassQName;
    private final Map<String,Type> mySubstitutor;

    private ClassType(String originalText, String classQName, Map<String, Type> substitutor) {
      myOriginalText = originalText;
      myClassQName = classQName;
      mySubstitutor = substitutor;
    }

    @Nonnull
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
      PsiElementFactory factory = facade.getElementFactory();
      PsiResolveHelper resolveHelper = facade.getResolveHelper();
      PsiClass aClass = resolveHelper.resolveReferencedClass(myClassQName, context);
      if (aClass == null) {
        return factory.createTypeFromText(myClassQName, context);
      }
      Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter,PsiType>();
      for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
        String name = typeParameter.getName();
        Type type = mySubstitutor.get(name);
        if (type != null) {
          substitutionMap.put(typeParameter, type.getType(context, manager));
        } else {
          substitutionMap.put(typeParameter, null);
        }
      }
      return factory.createType(aClass, factory.createSubstitutor(substitutionMap));
    }

    public String getTypeText() {
      return myOriginalText;
    }

    public void addImportsTo(JavaCodeFragment codeFragment) {
      codeFragment.addImportsFromString(myClassQName);
      Collection<Type> types = mySubstitutor.values();
      for (Type type : types) {
        if (type != null) {
          type.addImportsTo(codeFragment);
        }
      }
    }
  }

  private static class DisjunctionType extends Type {
    private final List<Type> myTypes;

    private DisjunctionType(List<Type> types) {
      myTypes = types;
    }

    @Nonnull
    @Override
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      List<PsiType> types = ContainerUtil.map(myTypes, type -> type.getType(context, manager));
      return new PsiDisjunctionType(types, manager);
    }

    @Override
    public String getTypeText() {
      return StringUtil.join(myTypes, type -> type.getTypeText(), "|");
    }

    @Override
    public void addImportsTo(JavaCodeFragment codeFragment) {
      for (Type type : myTypes) {
        type.addImportsTo(codeFragment);
      }
    }
  }

  private static class Creator extends PsiTypeVisitor<Type> {
    public static final Creator INSTANCE = new Creator();

    @Override
    public Type visitPrimitiveType(PsiPrimitiveType primitiveType) {
      return new Primitive(primitiveType);
    }

    @Override
    public Type visitEllipsisType(PsiEllipsisType ellipsisType) {
      return new Ellipsis(ellipsisType.getComponentType().accept(this));
    }

    @Override
    public Type visitArrayType(PsiArrayType arrayType) {
      return new Array(arrayType.getComponentType().accept(this));
    }

    @Override
    public Type visitWildcardType(PsiWildcardType wildcardType) {
      PsiType wildcardBound = wildcardType.getBound();
      Type bound = wildcardBound == null ? null : wildcardBound.accept(this);
      return new WildcardType(wildcardType.isExtends(), bound);
    }

    @Override
    public Type visitClassType(PsiClassType classType) {
      PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = resolveResult.getElement();
      if (aClass instanceof PsiAnonymousClass) {
        return visitClassType(((PsiAnonymousClass)aClass).getBaseClassType());
      }
      String originalText = classType.getPresentableText();
      if (aClass == null) {
        return new WrongType(originalText);
      } else {
        Map<String,Type> substitutionMap = new HashMap<String,Type>();
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
          PsiType type = substitutor.substitute(typeParameter);
          String name = typeParameter.getName();
          if (type == null) {
            substitutionMap.put(name, null);
          } else {
            substitutionMap.put(name, type.accept(this));
          }
        }
        String qualifiedName = aClass.getQualifiedName();
        LOG.assertTrue(aClass.getName() != null);
        return new ClassType(originalText, qualifiedName != null ? qualifiedName : aClass.getName(), substitutionMap);
      }
    }

    @Override
    public Type visitDisjunctionType(PsiDisjunctionType disjunctionType) {
      List<Type> types = ContainerUtil.map(disjunctionType.getDisjunctions(), type -> createTypeWrapper(type));
      return new DisjunctionType(types);
    }
  }

  public static Type createTypeWrapper(@Nonnull PsiType type) {
    return type.accept(Creator.INSTANCE);
  }
}
