/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.psi.util;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class PsiTypesUtil {
  @NonNls
  private static final Map<String, String> ourUnboxedTypes = new HashMap<>();
  @NonNls
  private static final Map<String, String> ourBoxedTypes = new HashMap<>();

  static {
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_BOOLEAN, "boolean");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_BYTE, "byte");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_SHORT, "short");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_INTEGER, "int");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_LONG, "long");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_FLOAT, "float");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_DOUBLE, "double");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_CHARACTER, "char");

    ourBoxedTypes.put("boolean", CommonClassNames.JAVA_LANG_BOOLEAN);
    ourBoxedTypes.put("byte", CommonClassNames.JAVA_LANG_BYTE);
    ourBoxedTypes.put("short", CommonClassNames.JAVA_LANG_SHORT);
    ourBoxedTypes.put("int", CommonClassNames.JAVA_LANG_INTEGER);
    ourBoxedTypes.put("long", CommonClassNames.JAVA_LANG_LONG);
    ourBoxedTypes.put("float", CommonClassNames.JAVA_LANG_FLOAT);
    ourBoxedTypes.put("double", CommonClassNames.JAVA_LANG_DOUBLE);
    ourBoxedTypes.put("char", CommonClassNames.JAVA_LANG_CHARACTER);
  }

  @NonNls
  private static final String GET_CLASS_METHOD = "getClass";

  private PsiTypesUtil() {
  }

  public static Object getDefaultValue(PsiType type) {
    if (!(type instanceof PsiPrimitiveType)) {
      return null;
    }
    switch (type.getCanonicalText()) {
      case "boolean":
        return false;
      case "byte":
        return (byte) 0;
      case "char":
        return '\0';
      case "short":
        return (short) 0;
      case "int":
        return 0;
      case "long":
        return 0L;
      case "float":
        return 0F;
      case "double":
        return 0D;
      default:
        return null;
    }
  }

  @Nonnull
  public static String getDefaultValueOfType(PsiType type) {
    return getDefaultValueOfType(type, false);
  }

  @Nonnull
  public static String getDefaultValueOfType(PsiType type, boolean customDefaultValues) {
    if (type instanceof PsiArrayType) {
      int count = type.getArrayDimensions() - 1;
      PsiType componentType = type.getDeepComponentType();

      if (componentType instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType) componentType;
        if (classType.resolve() instanceof PsiTypeParameter) {
          return PsiKeyword.NULL;
        }
      }

      PsiType erasedComponentType = TypeConversionUtil.erasure(componentType);
      StringBuilder buffer = new StringBuilder();
      buffer.append(PsiKeyword.NEW);
      buffer.append(" ");
      buffer.append(erasedComponentType.getCanonicalText());
      buffer.append("[0]");
      for (int i = 0; i < count; i++) {
        buffer.append("[]");
      }
      return buffer.toString();
    }
    if (type instanceof PsiPrimitiveType) {
      return PsiType.BOOLEAN.equals(type) ? PsiKeyword.FALSE : "0";
    }
    if (customDefaultValues) {
      PsiType rawType = type instanceof PsiClassType ? ((PsiClassType) type).rawType() : null;
      if (rawType != null && rawType.equalsToText(CommonClassNames.JAVA_UTIL_OPTIONAL)) {
        return CommonClassNames.JAVA_UTIL_OPTIONAL + ".empty()";
      }
    }
    return PsiKeyword.NULL;
  }

  /**
   * Returns the unboxed type name or parameter.
   *
   * @param type boxed java type name
   * @return unboxed type name if available; same value otherwise
   */
  @Contract("null -> null; !null -> !null")
  @Nullable
  public static String unboxIfPossible(final String type) {
    if (type == null) {
      return null;
    }
    final String s = ourUnboxedTypes.get(type);
    return s == null ? type : s;
  }

  /**
   * Returns the boxed type name or parameter.
   *
   * @param type primitive java type name
   * @return boxed type name if available; same value otherwise
   */
  @Contract("null -> null; !null -> !null")
  @Nullable
  public static String boxIfPossible(final String type) {
    if (type == null) {
      return null;
    }
    final String s = ourBoxedTypes.get(type);
    return s == null ? type : s;
  }

  @Nullable
  public static PsiClass getPsiClass(@Nullable PsiType psiType) {
    return psiType instanceof PsiClassType ? ((PsiClassType) psiType).resolve() : null;
  }

  public static PsiClassType getClassType(@Nonnull PsiClass psiClass) {
    return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
  }

  @Nullable
  public static PsiClassType getLowestUpperBoundClassType(@Nonnull final PsiDisjunctionType type) {
    final PsiType lub = type.getLeastUpperBound();
    if (lub instanceof PsiClassType) {
      return (PsiClassType) lub;
    } else if (lub instanceof PsiIntersectionType) {
      for (PsiType subType : ((PsiIntersectionType) lub).getConjuncts()) {
        if (subType instanceof PsiClassType) {
          final PsiClass aClass = ((PsiClassType) subType).resolve();
          if (aClass != null && !aClass.isInterface()) {
            return (PsiClassType) subType;
          }
        }
      }
    }
    return null;
  }

  public static PsiType patchMethodGetClassReturnType(@Nonnull PsiMethodReferenceExpression methodExpression, @Nonnull PsiMethod method) {
    if (isGetClass(method)) {
      final PsiType qualifierType = PsiMethodReferenceUtil.getQualifierType(methodExpression);
      return qualifierType != null ? createJavaLangClassType(methodExpression, qualifierType, true) : null;
    }
    return null;
  }

  public static PsiType patchMethodGetClassReturnType(@Nonnull PsiExpression call,
                                                      @Nonnull PsiReferenceExpression methodExpression,
                                                      @Nonnull PsiMethod method,
                                                      @Nullable Condition<IElementType> condition,
                                                      @Nonnull LanguageLevel languageLevel) {
    //JLS3 15.8.2
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) && isGetClass(method)) {
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      PsiType qualifierType = null;
      final Project project = call.getProject();
      if (qualifier != null) {
        qualifierType = TypeConversionUtil.erasure(qualifier.getType());
      } else if (condition != null) {
        ASTNode parent = call.getNode().getTreeParent();
        while (parent != null && condition.value(parent.getElementType())) {
          parent = parent.getTreeParent();
        }
        if (parent != null) {
          qualifierType = JavaPsiFacade.getInstance(project).getElementFactory().createType((PsiClass) parent.getPsi());
        }
      }
      return createJavaLangClassType(methodExpression, qualifierType, true);
    }
    return null;
  }

  public static boolean isGetClass(PsiMethod method) {
    if (GET_CLASS_METHOD.equals(method.getName())) {
      PsiClass aClass = method.getContainingClass();
      return aClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName());
    }
    return false;
  }

  @Nullable
  public static PsiType createJavaLangClassType(@Nonnull PsiElement context, @Nullable PsiType qualifierType, boolean captureTopLevelWildcards) {
    if (qualifierType != null) {
      PsiUtil.ensureValidType(qualifierType);
      JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
      PsiClass javaLangClass = facade.findClass(CommonClassNames.JAVA_LANG_CLASS, context.getResolveScope());
      if (javaLangClass != null && javaLangClass.getTypeParameters().length == 1) {
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.
            put(javaLangClass.getTypeParameters()[0], PsiWildcardType.createExtends(context.getManager(), qualifierType));
        final PsiClassType classType = facade.getElementFactory().createType(javaLangClass, substitutor, PsiUtil.getLanguageLevel(context));
        return captureTopLevelWildcards ? PsiUtil.captureToplevelWildcards(classType, context) : classType;
      }
    }
    return null;
  }

  /**
   * Return type explicitly declared in parent
   */
  @Nullable
  public static PsiType getExpectedTypeByParent(@Nonnull PsiElement element) {
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
    if (parent instanceof PsiVariable) {
      if (PsiUtil.checkSameExpression(element, ((PsiVariable) parent).getInitializer())) {
        PsiTypeElement typeElement = ((PsiVariable) parent).getTypeElement();
        if (typeElement != null && typeElement.isInferredType()) {
          return null;
        }
        return ((PsiVariable) parent).getType();
      }
    } else if (parent instanceof PsiAssignmentExpression) {
      if (PsiUtil.checkSameExpression(element, ((PsiAssignmentExpression) parent).getRExpression())) {
        return ((PsiAssignmentExpression) parent).getLExpression().getType();
      }
    } else if (parent instanceof PsiReturnStatement) {
      final PsiElement psiElement = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, PsiMethod.class);
      if (psiElement instanceof PsiLambdaExpression) {
        return null;
      } else if (psiElement instanceof PsiMethod) {
        return ((PsiMethod) psiElement).getReturnType();
      }
    } else if (PsiUtil.isCondition(element, parent)) {
      return PsiType.BOOLEAN.getBoxedType(parent);
    } else if (parent instanceof PsiArrayInitializerExpression) {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiNewExpression) {
        final PsiType type = ((PsiNewExpression) gParent).getType();
        if (type instanceof PsiArrayType) {
          return ((PsiArrayType) type).getComponentType();
        }
      } else if (gParent instanceof PsiVariable) {
        final PsiType type = ((PsiVariable) gParent).getType();
        if (type instanceof PsiArrayType) {
          return ((PsiArrayType) type).getComponentType();
        }
      } else if (gParent instanceof PsiArrayInitializerExpression) {
        final PsiType expectedTypeByParent = getExpectedTypeByParent(parent);
        return expectedTypeByParent != null && expectedTypeByParent instanceof PsiArrayType ? ((PsiArrayType) expectedTypeByParent).getComponentType() : null;
      }
    }
    return null;
  }

  /**
   * Returns the return type for enclosing method or lambda
   *
   * @param element element inside method or lambda to determine the return type of
   * @return the return type or null if cannot be determined
   */
  @Nullable
  public static PsiType getMethodReturnType(PsiElement element) {
    final PsiElement methodOrLambda = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiLambdaExpression.class);
    return methodOrLambda instanceof PsiMethod ? ((PsiMethod) methodOrLambda).getReturnType() : methodOrLambda instanceof PsiLambdaExpression ? LambdaUtil.getFunctionalInterfaceReturnType(
        (PsiLambdaExpression) methodOrLambda) : null;
  }

  public static boolean compareTypes(PsiType leftType, PsiType rightType, boolean ignoreEllipsis) {
    if (ignoreEllipsis) {
      if (leftType instanceof PsiEllipsisType) {
        leftType = ((PsiEllipsisType) leftType).toArrayType();
      }
      if (rightType instanceof PsiEllipsisType) {
        rightType = ((PsiEllipsisType) rightType).toArrayType();
      }
    }
    return Comparing.equal(leftType, rightType);
  }

  /**
   * @deprecated not compliant to specification, use {@link PsiTypesUtil#isDenotableType(PsiType, PsiElement)} instead
   */
  @Deprecated
  public static boolean isDenotableType(@Nullable PsiType type) {
    return !(type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType);
  }

  /**
   * @param context in which type should be checked
   * @return false if type is null or has no explicit canonical type representation (e. g. intersection type)
   */
  public static boolean isDenotableType(@Nullable PsiType type, @Nonnull PsiElement context) {
    if (type == null || type instanceof PsiWildcardType)
      return false;
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(context.getProject());
    try {
      PsiType typeAfterReplacement = elementFactory.createTypeElementFromText(type.getCanonicalText(), context).getType();
      return type.equals(typeAfterReplacement);
    } catch (IncorrectOperationException e) {
      return false;
    }
  }

  public static boolean hasUnresolvedComponents(@Nonnull PsiType type) {
    return type.accept(new PsiTypeVisitor<Boolean>() {
      @Nullable
      @Override
      public Boolean visitClassType(PsiClassType classType) {
        PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
        final PsiClass psiClass = resolveResult.getElement();
        if (psiClass == null) {
          return true;
        }
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        for (PsiTypeParameter param : PsiUtil.typeParametersIterable(psiClass)) {
          PsiType psiType = substitutor.substitute(param);
          if (psiType != null && psiType.accept(this)) {
            return true;
          }
        }
        return super.visitClassType(classType);
      }

      @Nullable
      @Override
      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @Nonnull
      @Override
      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        return bound != null && bound.accept(this);
      }

      @Override
      public Boolean visitType(PsiType type) {
        return false;
      }
    });
  }

  public static PsiType getParameterType(PsiParameter[] parameters, int i, boolean varargs) {
    final PsiParameter parameter = parameters[i < parameters.length ? i : parameters.length - 1];
    PsiType parameterType = parameter.getType();
    if (parameterType instanceof PsiEllipsisType && varargs) {
      parameterType = ((PsiEllipsisType) parameterType).getComponentType();
    }
    if (!parameterType.isValid()) {
      PsiUtil.ensureValidType(parameterType, "Invalid type of parameter " + parameter + " of " + parameter.getClass());
    }
    return parameterType;
  }

  @Nonnull
  public static PsiTypeParameter[] filterUnusedTypeParameters(@Nonnull PsiTypeParameter[] typeParameters, final PsiType... types) {
    if (typeParameters.length == 0) {
      return PsiTypeParameter.EMPTY_ARRAY;
    }

    TypeParameterSearcher searcher = new TypeParameterSearcher();
    for (PsiType type : types) {
      type.accept(searcher);
    }
    return searcher.getTypeParameters().toArray(PsiTypeParameter.EMPTY_ARRAY);
  }

  @Nonnull
  public static PsiTypeParameter[] filterUnusedTypeParameters(final PsiType superReturnTypeInBaseClassType, @Nonnull PsiTypeParameter[] typeParameters) {
    return filterUnusedTypeParameters(typeParameters, superReturnTypeInBaseClassType);
  }

  public static boolean isAccessibleAt(PsiTypeParameter parameter, PsiElement context) {
    PsiTypeParameterListOwner owner = parameter.getOwner();
    if (owner instanceof PsiMethod) {
      return PsiTreeUtil.isAncestor(owner, context, false);
    }
    if (owner instanceof PsiClass) {
      return PsiTreeUtil.isAncestor(owner, context, false) && InheritanceUtil.hasEnclosingInstanceInScope((PsiClass) owner, context, false, false);
    }
    return false;
  }

  public static boolean allTypeParametersResolved(PsiElement context, PsiType targetType) {
    TypeParameterSearcher searcher = new TypeParameterSearcher();
    targetType.accept(searcher);
    Set<PsiTypeParameter> parameters = searcher.getTypeParameters();
    return parameters.stream().allMatch(parameter -> isAccessibleAt(parameter, context));
  }

  @Nonnull
  public static PsiType createArrayType(@Nonnull PsiType newType, int arrayDim) {
    for (int i = 0; i < arrayDim; i++) {
      newType = newType.createArrayType();
    }
    return newType;
  }

  /**
   * @return null if type can't be explicitly specified
   */
  @Nullable
  public static PsiTypeElement replaceWithExplicitType(PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (!isDenotableType(type, typeElement)) {
      return null;
    }
    Project project = typeElement.getProject();
    PsiTypeElement typeElementByExplicitType = JavaPsiFacade.getElementFactory(project).createTypeElement(type);
    PsiElement explicitTypeElement = typeElement.replace(typeElementByExplicitType);
    explicitTypeElement = JavaCodeStyleManager.getInstance(project).shortenClassReferences(explicitTypeElement);
    return (PsiTypeElement)CodeStyleManager.getInstance(project).reformat(explicitTypeElement);
  }

  /**
   * Checks if {@code type} mentions type parameters from the passed {@code Set}
   * Implicit type arguments of types based on inner classes of generic outer classes are explicitly checked
   */
  public static boolean mentionsTypeParameters(@Nullable PsiType type, @Nonnull Set<? extends PsiTypeParameter> typeParameters) {
    return mentionsTypeParametersOrUnboundedWildcard(type, typeParameters::contains);
  }

  public static boolean mentionsTypeParameters(@Nullable PsiType type, @Nonnull Predicate<? super PsiTypeParameter> wantedTypeParameter) {
    return mentionsTypeParametersOrUnboundedWildcard(type, wantedTypeParameter);
  }

  private static boolean mentionsTypeParametersOrUnboundedWildcard(@Nullable PsiType type,
                                                                   final Predicate<? super PsiTypeParameter> wantedTypeParameter) {
    if (type == null) return false;
    return type.accept(new PsiTypeVisitor<Boolean>() {
      @Override
      public Boolean visitType(@Nonnull PsiType type) {
        return false;
      }

      @Override
      public Boolean visitWildcardType(@Nonnull PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        return bound != null ? bound.accept(this)
          : Boolean.valueOf(false);
      }

      @Override
      public Boolean visitClassType(@Nonnull PsiClassType classType) {
        PsiClassType.ClassResolveResult result = classType.resolveGenerics();
        final PsiClass psiClass = result.getElement();
        if (psiClass != null) {
          PsiSubstitutor substitutor = result.getSubstitutor();
          for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(psiClass)) {
            PsiType type = substitutor.substitute(parameter);
            if (type != null && type.accept(this)) return true;
          }
        }
        return psiClass instanceof PsiTypeParameter && wantedTypeParameter.test((PsiTypeParameter)psiClass);
      }

      @Override
      public Boolean visitIntersectionType(@Nonnull PsiIntersectionType intersectionType) {
        for (PsiType conjunct : intersectionType.getConjuncts()) {
          if (conjunct.accept(this)) return true;
        }
        return false;
      }

      @Override
      public Boolean visitMethodReferenceType(@Nonnull PsiMethodReferenceType methodReferenceType) {
        return false;
      }

      @Override
      public Boolean visitLambdaExpressionType(@Nonnull PsiLambdaExpressionType lambdaExpressionType) {
        return false;
      }

      @Override
      public Boolean visitArrayType(@Nonnull PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }
    });
  }

  public static class TypeParameterSearcher extends PsiTypeVisitor<Boolean> {
    private final Set<PsiTypeParameter> myTypeParams = new HashSet<>();

    public Set<PsiTypeParameter> getTypeParameters() {
      return myTypeParams;
    }

    public Boolean visitType(final PsiType type) {
      return false;
    }

    public Boolean visitArrayType(final PsiArrayType arrayType) {
      return arrayType.getComponentType().accept(this);
    }

    public Boolean visitClassType(final PsiClassType classType) {
      PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass instanceof PsiTypeParameter) {
        myTypeParams.add((PsiTypeParameter) aClass);
      }

      if (aClass != null) {
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        for (final PsiTypeParameter parameter : PsiUtil.typeParametersIterable(aClass)) {
          PsiType psiType = substitutor.substitute(parameter);
          if (psiType != null) {
            psiType.accept(this);
          }
        }
      }
      return false;
    }

    public Boolean visitWildcardType(final PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      if (bound != null) {
        bound.accept(this);
      }
      return false;
    }
  }
}
