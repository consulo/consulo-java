/*
 * Copyright 2008-2013 Bas Leijdekkers
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
package com.intellij.java.impl.ig.psiutils;

import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.application.util.query.Query;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class WeakestTypeFinder {

  private WeakestTypeFinder() {
  }

  @Nonnull
  public static Collection<PsiClass> calculateWeakestClassesNecessary(@Nonnull PsiElement variableOrMethod,
                                                                      boolean useRighthandTypeAsWeakestTypeInAssignments,
                                                                      boolean useParameterizedTypeForCollectionMethods) {
    PsiType variableOrMethodType;
    if (variableOrMethod instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable) variableOrMethod;
      variableOrMethodType = variable.getType();
    } else if (variableOrMethod instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) variableOrMethod;
      variableOrMethodType = method.getReturnType();
      if (PsiType.VOID.equals(variableOrMethodType)) {
        return Collections.emptyList();
      }
    } else {
      throw new IllegalArgumentException("PsiMethod or PsiVariable expected: " + variableOrMethod);
    }
    if (!(variableOrMethodType instanceof PsiClassType)) {
      return Collections.emptyList();
    }
    PsiClassType variableOrMethodClassType = (PsiClassType) variableOrMethodType;
    PsiClass variableOrMethodClass = variableOrMethodClassType.resolve();
    if (variableOrMethodClass == null) {
      return Collections.emptyList();
    }
    Set<PsiClass> weakestTypeClasses = new HashSet<PsiClass>();
    GlobalSearchScope scope = variableOrMethod.getResolveScope();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(variableOrMethod.getProject());
    PsiClass lowerBoundClass;
    if (variableOrMethod instanceof PsiResourceVariable) {
      lowerBoundClass = facade.findClass(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, scope);
      if (lowerBoundClass == null || variableOrMethodClass.equals(lowerBoundClass)) {
        return Collections.emptyList();
      }
      weakestTypeClasses.add(lowerBoundClass);
      PsiResourceVariable resourceVariable = (PsiResourceVariable) variableOrMethod;
      @NonNls String methodCallText = resourceVariable.getName() + ".close()";
      PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression) facade.getElementFactory().createExpressionFromText(methodCallText, resourceVariable.getParent());
      if (!findWeakestType(methodCallExpression, weakestTypeClasses)) {
        return Collections.emptyList();
      }
    } else {
      lowerBoundClass = facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, scope);
      if (lowerBoundClass == null || variableOrMethodClass.equals(lowerBoundClass)) {
        return Collections.emptyList();
      }
      weakestTypeClasses.add(lowerBoundClass);
    }

    Query<PsiReference> query = ReferencesSearch.search(variableOrMethod, variableOrMethod.getUseScope());
    boolean hasUsages = false;
    for (PsiReference reference : query) {
      if (reference == null) {
        continue;
      }
      hasUsages = true;
      PsiElement referenceElement = reference.getElement();
      PsiElement referenceParent = referenceElement.getParent();
      if (referenceParent instanceof PsiMethodCallExpression) {
        referenceElement = referenceParent;
        referenceParent = referenceElement.getParent();
      }
      PsiElement referenceGrandParent = referenceParent.getParent();
      if (referenceParent instanceof PsiExpressionList) {
        if (!(referenceGrandParent instanceof PsiMethodCallExpression)) {
          return Collections.emptyList();
        }
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) referenceGrandParent;
        if (!findWeakestType(referenceElement, methodCallExpression, useParameterizedTypeForCollectionMethods, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      } else if (referenceGrandParent instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) referenceGrandParent;
        if (!findWeakestType(methodCallExpression, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      } else if (referenceParent instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) referenceParent;
        if (!findWeakestType(referenceElement, assignmentExpression, useRighthandTypeAsWeakestTypeInAssignments, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      } else if (referenceParent instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable) referenceParent;
        PsiType type = variable.getType();
        if (!checkType(type, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      } else if (referenceParent instanceof PsiForeachStatement) {
        PsiForeachStatement foreachStatement = (PsiForeachStatement) referenceParent;
        if (!Comparing.equal(foreachStatement.getIteratedValue(), referenceElement)) {
          return Collections.emptyList();
        }
        PsiClass javaLangIterableClass = facade.findClass(CommonClassNames.JAVA_LANG_ITERABLE, scope);
        if (javaLangIterableClass == null) {
          return Collections.emptyList();
        }
        checkClass(javaLangIterableClass, weakestTypeClasses);
      } else if (referenceParent instanceof PsiReturnStatement) {
        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(referenceParent, PsiMethod.class);
        if (containingMethod == null) {
          return Collections.emptyList();
        }
        PsiType type = containingMethod.getReturnType();
        if (!checkType(type, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      } else if (referenceParent instanceof PsiReferenceExpression) {
        // field access, method call is handled above.
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression) referenceParent;
        PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiField)) {
          return Collections.emptyList();
        }
        PsiField field = (PsiField) target;
        PsiClass containingClass = field.getContainingClass();
        checkClass(containingClass, weakestTypeClasses);
      } else if (referenceParent instanceof PsiArrayInitializerExpression) {
        PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression) referenceParent;
        if (!findWeakestType(arrayInitializerExpression, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      } else if (referenceParent instanceof PsiThrowStatement) {
        PsiThrowStatement throwStatement = (PsiThrowStatement) referenceParent;
        if (!findWeakestType(throwStatement, variableOrMethodClass, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      } else if (referenceParent instanceof PsiConditionalExpression) {
        PsiConditionalExpression conditionalExpression = (PsiConditionalExpression) referenceParent;
        PsiExpression condition = conditionalExpression.getCondition();
        if (referenceElement.equals(condition)) {
          return Collections.emptyList();
        }
        PsiType type = ExpectedTypeUtils.findExpectedType(conditionalExpression, true);
        if (!checkType(type, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      } else if (referenceParent instanceof PsiBinaryExpression) {
        // strings only
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression) referenceParent;
        PsiType type = binaryExpression.getType();
        if (variableOrMethodType.equals(type)) {
          if (!checkType(type, weakestTypeClasses)) {
            return Collections.emptyList();
          }
        }
      } else if (referenceParent instanceof PsiSwitchStatement) {
        // only enums and primitives can be a switch expression
        return Collections.emptyList();
      } else if (referenceParent instanceof PsiPrefixExpression) {
        // only primitives and boxed types are the target of a prefix
        // expression
        return Collections.emptyList();
      } else if (referenceParent instanceof PsiPostfixExpression) {
        // only primitives and boxed types are the target of a postfix
        // expression
        return Collections.emptyList();
      } else if (referenceParent instanceof PsiIfStatement) {
        // only booleans and boxed Booleans are the condition of an if
        // statement
        return Collections.emptyList();
      } else if (referenceParent instanceof PsiForStatement) {
        // only booleans and boxed Booleans are the condition of an
        // for statement
        return Collections.emptyList();
      } else if (referenceParent instanceof PsiNewExpression) {
        PsiNewExpression newExpression = (PsiNewExpression) referenceParent;
        PsiExpression qualifier = newExpression.getQualifier();
        if (qualifier != null) {
          PsiType type = newExpression.getType();
          if (!(type instanceof PsiClassType)) {
            return Collections.emptyList();
          }
          PsiClassType classType = (PsiClassType) type;
          PsiClass innerClass = classType.resolve();
          if (innerClass == null) {
            return Collections.emptyList();
          }
          PsiClass outerClass = innerClass.getContainingClass();
          if (outerClass != null) {
            checkClass(outerClass, weakestTypeClasses);
          }
        }
      }
      if (weakestTypeClasses.contains(variableOrMethodClass) || weakestTypeClasses.isEmpty()) {
        return Collections.emptyList();
      }
    }
    if (!hasUsages) {
      return Collections.emptyList();
    }
    weakestTypeClasses = filterAccessibleClasses(weakestTypeClasses, variableOrMethod);
    return weakestTypeClasses;
  }

  private static boolean findWeakestType(PsiElement referenceElement,
                                         PsiMethodCallExpression methodCallExpression,
                                         boolean useParameterizedTypeForCollectionMethods,
                                         Set<PsiClass> weakestTypeClasses) {
    if (!(referenceElement instanceof PsiExpression)) {
      return false;
    }
    JavaResolveResult resolveResult = methodCallExpression.resolveMethodGenerics();
    PsiMethod method = (PsiMethod) resolveResult.getElement();
    if (method == null) {
      return false;
    }
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiExpressionList expressionList = methodCallExpression.getArgumentList();
    PsiExpression[] expressions = expressionList.getExpressions();
    int index = ArrayUtil.indexOf(expressions, referenceElement);
    if (index < 0) {
      return false;
    }
    PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() == 0) {
      return false;
    }
    PsiParameter[] parameters = parameterList.getParameters();
    PsiParameter parameter;
    PsiType type;
    if (index < parameters.length) {
      parameter = parameters[index];
      type = parameter.getType();
    } else {
      parameter = parameters[parameters.length - 1];
      type = parameter.getType();
      if (!(type instanceof PsiEllipsisType)) {
        return false;
      }
    }
    if (!useParameterizedTypeForCollectionMethods) {
      return checkType(type, substitutor, weakestTypeClasses);
    }
    @NonNls String methodName = method.getName();
    if (HardcodedMethodConstants.REMOVE.equals(methodName) ||
        HardcodedMethodConstants.GET.equals(methodName) ||
        "containsKey".equals(methodName) ||
        "containsValue".equals(methodName) ||
        "contains".equals(methodName) ||
        HardcodedMethodConstants.INDEX_OF.equals(methodName) ||
        HardcodedMethodConstants.LAST_INDEX_OF.equals(methodName)) {
      PsiClass containingClass = method.getContainingClass();
      if (InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_MAP) ||
          InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier != null) {
          PsiType qualifierType = qualifier.getType();
          if (qualifierType instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) qualifierType;
            PsiType[] parameterTypes = classType.getParameters();
            if (parameterTypes.length > 0) {
              PsiType parameterType = parameterTypes[0];
              PsiExpression expression = expressions[index];
              PsiType expressionType = expression.getType();
              if (expressionType == null || parameterType == null || !parameterType.isAssignableFrom(expressionType)) {
                return false;
              }
              return checkType(parameterType, substitutor, weakestTypeClasses);
            }
          }
        }
      }
    }
    return checkType(type, substitutor, weakestTypeClasses);
  }

  private static boolean checkType(@Nullable PsiType type, @Nonnull PsiSubstitutor substitutor,
                                   @Nonnull Collection<PsiClass> weakestTypeClasses) {
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    PsiClassType classType = (PsiClassType) type;
    PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return false;
    }
    if (aClass instanceof PsiTypeParameter) {
      PsiType substitution = substitutor.substitute((PsiTypeParameter) aClass);
      return checkType(substitution, weakestTypeClasses);
    }
    checkClass(aClass, weakestTypeClasses);
    return true;
  }

  private static boolean findWeakestType(PsiMethodCallExpression methodCallExpression, Set<PsiClass> weakestTypeClasses) {
    PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    PsiElement target = methodExpression.resolve();
    if (!(target instanceof PsiMethod)) {
      return false;
    }
    PsiMethod method = (PsiMethod) target;
    PsiReferenceList throwsList = method.getThrowsList();
    PsiClassType[] classTypes = throwsList.getReferencedTypes();
    Collection<PsiClassType> thrownTypes = new HashSet<PsiClassType>(Arrays.asList(classTypes));
    List<PsiMethod> superMethods = findAllSuperMethods(method);
    boolean checked = false;
    if (!superMethods.isEmpty()) {
      PsiType expectedType = ExpectedTypeUtils.findExpectedType(methodCallExpression, false);
      for (PsiMethod superMethod : superMethods) {
        PsiType returnType = superMethod.getReturnType();
        if (expectedType != null && returnType != null && !expectedType.isAssignableFrom(returnType)) {
          continue;
        }
        if (throwsIncompatibleException(superMethod, thrownTypes)) {
          continue;
        }
        if (!PsiUtil.isAccessible(superMethod, methodCallExpression, null)) {
          continue;
        }
        PsiClass containingClass = superMethod.getContainingClass();
        checkClass(containingClass, weakestTypeClasses);
        checked = true;
      }
    }
    if (!checked) {
      PsiType returnType = method.getReturnType();
      if (returnType instanceof PsiClassType) {
        PsiClassType classType = (PsiClassType) returnType;
        PsiClass aClass = classType.resolve();
        if (aClass instanceof PsiTypeParameter) {
          return false;
        }
      }
      PsiClass containingClass = method.getContainingClass();
      checkClass(containingClass, weakestTypeClasses);
    }
    return true;
  }

  private static List<PsiMethod> findAllSuperMethods(PsiMethod method) {
    List<PsiMethod> methods = findAllSuperMethods(method, new ArrayList());
    Collections.reverse(methods);
    return methods;
  }

  private static List<PsiMethod> findAllSuperMethods(PsiMethod method, List<PsiMethod> result) {
    PsiMethod[] superMethods = method.findSuperMethods();
    Collections.addAll(result, superMethods);
    for (PsiMethod superMethod : superMethods) {
      findAllSuperMethods(superMethod, result);
    }
    return result;
  }

  private static boolean findWeakestType(PsiElement referenceElement, PsiAssignmentExpression assignmentExpression,
                                         boolean useRighthandTypeAsWeakestTypeInAssignments, Set<PsiClass> weakestTypeClasses) {
    IElementType tokenType = assignmentExpression.getOperationTokenType();
    if (JavaTokenType.EQ != tokenType) {
      return false;
    }
    PsiExpression lhs = assignmentExpression.getLExpression();
    PsiExpression rhs = assignmentExpression.getRExpression();
    PsiType lhsType = lhs.getType();
    if (referenceElement.equals(rhs)) {
      if (!checkType(lhsType, weakestTypeClasses)) {
        return false;
      }
    } else if (useRighthandTypeAsWeakestTypeInAssignments) {
      if (rhs == null) {
        return false;
      }
      if (!(rhs instanceof PsiNewExpression) || !(rhs instanceof PsiTypeCastExpression)) {
        PsiType rhsType = rhs.getType();
        if (lhsType == null || lhsType.equals(rhsType)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean findWeakestType(PsiArrayInitializerExpression arrayInitializerExpression, Set<PsiClass> weakestTypeClasses) {
    PsiType type = arrayInitializerExpression.getType();
    if (!(type instanceof PsiArrayType)) {
      return false;
    }
    PsiArrayType arrayType = (PsiArrayType) type;
    PsiType componentType = arrayType.getComponentType();
    return checkType(componentType, weakestTypeClasses);
  }

  private static boolean findWeakestType(PsiThrowStatement throwStatement, PsiClass variableOrMethodClass,
                                         Set<PsiClass> weakestTypeClasses) {
    PsiClassType runtimeExceptionType = TypeUtils.getType(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, throwStatement);
    PsiClass runtimeExceptionClass = runtimeExceptionType.resolve();
    if (runtimeExceptionClass != null && InheritanceUtil.isInheritorOrSelf(variableOrMethodClass, runtimeExceptionClass, true)) {
      if (!checkType(runtimeExceptionType, weakestTypeClasses)) {
        return false;
      }
    } else {
      PsiMethod method = PsiTreeUtil.getParentOfType(throwStatement, PsiMethod.class);
      if (method == null) {
        return false;
      }
      PsiReferenceList throwsList = method.getThrowsList();
      PsiClassType[] referencedTypes = throwsList.getReferencedTypes();
      boolean checked = false;
      for (PsiClassType referencedType : referencedTypes) {
        PsiClass throwableClass = referencedType.resolve();
        if (throwableClass == null ||
            !InheritanceUtil.isInheritorOrSelf(variableOrMethodClass, throwableClass, true)) {
          continue;
        }
        if (!checkType(referencedType, weakestTypeClasses)) {
          continue;
        }
        checked = true;
        break;
      }
      if (!checked) {
        return false;
      }
    }
    return true;
  }

  private static boolean throwsIncompatibleException(PsiMethod method, Collection<PsiClassType> exceptionTypes) {
    PsiReferenceList superThrowsList = method.getThrowsList();
    PsiClassType[] superThrownTypes = superThrowsList.getReferencedTypes();
    outer:
    for (PsiClassType superThrownType : superThrownTypes) {
      if (exceptionTypes.contains(superThrownType)) {
        continue;
      }
      for (PsiClassType exceptionType : exceptionTypes) {
        if (InheritanceUtil.isInheritor(superThrownType, exceptionType.getCanonicalText())) {
          continue outer;
        }
      }
      PsiClass aClass = superThrownType.resolve();
      if (aClass == null) {
        return true;
      }
      if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION)
          && !InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_ERROR)) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkType(@Nullable PsiType type, @Nonnull Collection<PsiClass> weakestTypeClasses) {
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    PsiClassType classType = (PsiClassType) type;
    PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return false;
    }
    checkClass(aClass, weakestTypeClasses);
    return true;
  }

  public static Set<PsiClass> filterAccessibleClasses(Set<PsiClass> weakestTypeClasses, PsiElement context) {
    Set<PsiClass> result = new HashSet<PsiClass>();
    for (PsiClass weakestTypeClass : weakestTypeClasses) {
      if (PsiUtil.isAccessible(weakestTypeClass, context, null)) {
        result.add(weakestTypeClass);
        continue;
      }
      PsiClass visibleInheritor = getVisibleInheritor(weakestTypeClass, context);
      if (visibleInheritor != null) {
        result.add(visibleInheritor);
      }
    }
    return result;
  }

  @Nullable
  private static PsiClass getVisibleInheritor(PsiClass superClass, PsiElement context) {
    Query<PsiClass> search = DirectClassInheritorsSearch.search(superClass, context.getResolveScope());
    for (PsiClass aClass : search) {
      if (superClass.isInheritor(aClass, true)) {
        if (PsiUtil.isAccessible(aClass, context, null)) {
          return aClass;
        } else {
          return getVisibleInheritor(aClass, context);
        }
      }
    }
    return null;
  }

  private static void checkClass(@Nullable PsiClass aClass, @Nonnull Collection<PsiClass> weakestTypeClasses) {
    if (aClass == null) {
      return;
    }
    boolean shouldAdd = true;
    for (Iterator<PsiClass> iterator = weakestTypeClasses.iterator(); iterator.hasNext(); ) {
      PsiClass weakestTypeClass = iterator.next();
      if (!weakestTypeClass.equals(aClass)) {
        if (aClass.isInheritor(weakestTypeClass, true)) {
          iterator.remove();
        } else if (weakestTypeClass.isInheritor(aClass, true)) {
          shouldAdd = false;
        } else {
          iterator.remove();
          shouldAdd = false;
        }
      } else {
        shouldAdd = false;
      }
    }
    if (shouldAdd) {
      weakestTypeClasses.add(aClass);
    }
  }
}
