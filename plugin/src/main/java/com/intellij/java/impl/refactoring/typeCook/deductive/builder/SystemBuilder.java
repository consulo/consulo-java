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
package com.intellij.java.impl.refactoring.typeCook.deductive.builder;

import com.intellij.java.impl.refactoring.typeCook.Settings;
import com.intellij.java.impl.refactoring.typeCook.Util;
import com.intellij.java.impl.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.java.impl.refactoring.typeCook.deductive.util.VictimCollector;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.PsiTypeVariable;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiAnchor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * User: db
 * Date: 27.06.2003
 * Time: 22:48:08
 */
public class SystemBuilder {
  private static final Logger LOG = Logger.getInstance(SystemBuilder.class);

  private final PsiManager myManager;
  private final HashMap<PsiElement, Boolean> myMethodCache;
  private final HashMap<PsiParameter, PsiParameter> myParameters;
  private final HashMap<PsiMethod, PsiMethod> myMethods;
  private final HashMap<PsiElement, PsiType> myTypes;
  private final HashSet<PsiAnchor> myVisitedConstructions;
  private final Settings mySettings;
  private final PsiTypeVariableFactory myTypeVariableFactory;
  private final Project myProject;

  public SystemBuilder(Project project, Settings settings) {
    myProject = project;
    myManager = PsiManager.getInstance(myProject);
    mySettings = settings;
    myMethodCache = new HashMap<PsiElement, Boolean>();
    myParameters = new HashMap<PsiParameter, PsiParameter>();
    myMethods = new HashMap<PsiMethod, PsiMethod>();
    myTypes = new HashMap<PsiElement, PsiType>();
    myVisitedConstructions = new HashSet<PsiAnchor>();
    myTypeVariableFactory = new PsiTypeVariableFactory();
  }

  private HashSet<PsiElement> collect(PsiElement[] scopes) {
    return new VictimCollector(scopes, mySettings).getVictims();
  }

  private boolean verifyMethod(PsiElement element, HashSet<PsiElement> victims, PsiSearchHelper helper) {
    PsiMethod method;
    PsiParameter parameter = null;
    int index = 0;

    if (element instanceof PsiMethod) {
      method = (PsiMethod)element;
    }
    else if (element instanceof PsiParameter) {
      parameter = (PsiParameter)element;
      method = (PsiMethod)parameter.getDeclarationScope();
      index = method.getParameterList().getParameterIndex(parameter);
    }
    else {
      LOG.error("Parameter or method expected, but found " + (element == null ? "null" : element.getClass().getName()));
      return false;
    }

    PsiMethod superMethod = method.findDeepestSuperMethod();
    PsiMethod keyMethod;
    PsiParameter keyParameter = null;

    if (superMethod != null) {
      Boolean good = myMethodCache.get(superMethod);

      if (good != null && !good.booleanValue()) {
        return false;
      }

      PsiElement e = parameter == null ? superMethod : superMethod.getParameterList().getParameters()[index];

      if (!victims.contains(e)) {
        myMethodCache.put(superMethod, Boolean.FALSE);
        return false;
      }

      keyMethod = superMethod;

      myMethods.put(method, keyMethod);

      if (parameter != null) {
        keyParameter = (PsiParameter)e;
      }
    }
    else {
      Boolean good = myMethodCache.get(method);

      if (good != null && good.booleanValue()) {
        if (myMethods.get(method) == null) {
          myMethods.put(method, method);
        }

        if (parameter != null && myParameters.get(parameter) == null) {
          myParameters.put(parameter, parameter);
        }

        return true;
      }

      keyMethod = method;
      keyParameter = parameter;
    }

    PsiMethod[] overriders = OverridingMethodsSearch.search(keyMethod, true).toArray(PsiMethod.EMPTY_ARRAY);

    for (PsiMethod overrider : overriders) {
      PsiElement e = parameter != null ? overrider.getParameterList().getParameters()[index] : overrider;

      if (!victims.contains(e)) {
        myMethodCache.put(keyMethod, Boolean.FALSE);
        return false;
      }
    }

    for (PsiMethod overrider : overriders) {
      PsiElement e = parameter != null ? overrider.getParameterList().getParameters()[index] : overrider;

      myMethods.put(overrider, keyMethod);

      if (parameter != null) {
        myParameters.put((PsiParameter)e, keyParameter);
      }
    }

    myMethods.put(method, keyMethod);

    if (parameter != null) {
      myParameters.put(parameter, keyParameter);
    }

    myMethodCache.put(keyMethod, Boolean.TRUE);

    return true;
  }

  private void setType(PsiElement e, PsiType t) {
    myTypes.put(e, t);
  }

  private PsiType defineType(PsiElement e) {
    PsiType t = myTypes.get(e);

    if (t != null) {
      return t;
    }

    t = Util.getType(e);

    PsiType parameterizedType = Util.createParameterizedType(t, myTypeVariableFactory, e);

    myTypes.put(e, parameterizedType);

    return parameterizedType;
  }

  private PsiType getType(PsiElement e) {
    PsiType t = myTypes.get(e);

    if (t != null) {
      return t;
    }

    return Util.banalize(Util.getType(e));
  }

  private boolean isCooked(PsiElement element) {
    return myTypes.get(element) != null;
  }

  public PsiType inferTypeForMethodTypeParameter(PsiTypeParameter typeParameter,
                                                 PsiParameter[] parameters,
                                                 PsiExpression[] arguments,
                                                 PsiSubstitutor partialSubstitutor,
                                                 PsiElement parent,
                                                 ReductionSystem system) {
    PsiType substitution = PsiType.NULL;
    PsiResolveHelper helper = JavaPsiFacade.getInstance(typeParameter.getProject()).getResolveHelper();
    if (parameters.length > 0) {
      for (int j = 0; j < arguments.length; j++) {
        PsiExpression argument = arguments[j];
        PsiParameter parameter = parameters[Math.min(j, parameters.length - 1)];
        if (j >= parameters.length && !parameter.isVarArgs()) break;
        PsiType parameterType = parameter.getType();
        PsiType argumentType = evaluateType(argument, system);

        if (parameterType instanceof PsiEllipsisType) {
          parameterType = ((PsiEllipsisType)parameterType).getComponentType();
          if (arguments.length == parameters.length &&
              argumentType instanceof PsiArrayType &&
              !(((PsiArrayType)argumentType).getComponentType() instanceof PsiPrimitiveType)) {
            argumentType = ((PsiArrayType)argumentType).getComponentType();
          }
        }
        PsiType currentSubstitution =
          helper.getSubstitutionForTypeParameter(typeParameter, parameterType, argumentType, true, PsiUtil.getLanguageLevel(parent));
        if (currentSubstitution == null) {
          substitution = null;
          break;
        }
        else if (currentSubstitution instanceof PsiWildcardType) {
          if (substitution instanceof PsiWildcardType) return PsiType.NULL;
        }
        else if (PsiType.NULL.equals(currentSubstitution)) continue;

        if (PsiType.NULL.equals(substitution)) {
          substitution = currentSubstitution;
          continue;
        }
        if (!substitution.equals(currentSubstitution)) {
          if (substitution instanceof PsiTypeVariable || currentSubstitution instanceof PsiTypeVariable) {
            substitution = GenericsUtil.getLeastUpperBound(substitution, currentSubstitution, typeParameter.getManager());
            if (substitution == null) break;
          }
        }
      }
    }

    if (PsiType.NULL.equals(substitution)) {
      substitution = inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent, system);
    }
    return substitution;
  }

  private PsiType inferMethodTypeParameterFromParent(PsiTypeParameter typeParameter,
                                                     PsiSubstitutor substitutor,
                                                     PsiElement parent,
                                                     ReductionSystem system) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    PsiType substitution = PsiType.NULL;
    if (owner instanceof PsiMethod) {
      if (parent instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parent;
        substitution = inferMethodTypeParameterFromParent(methodCall.getParent(), methodCall, typeParameter, substitutor, system);
      }
    }
    return substitution;
  }

  private PsiType inferMethodTypeParameterFromParent(PsiElement parent,
                                                     PsiMethodCallExpression methodCall,
                                                     PsiTypeParameter typeParameter,
                                                     PsiSubstitutor substitutor,
                                                     ReductionSystem system) {
    PsiType type = null;

    if (parent instanceof PsiVariable && methodCall.equals(((PsiVariable)parent).getInitializer())) {
      type = getType(parent);
    }
    else if (parent instanceof PsiAssignmentExpression && methodCall.equals(((PsiAssignmentExpression)parent).getRExpression())) {
      type = evaluateType(((PsiAssignmentExpression)parent).getLExpression(), system);
    }
    else if (parent instanceof PsiTypeCastExpression && methodCall.equals(((PsiTypeCastExpression)parent).getOperand())) {
      type = evaluateType((PsiExpression)parent, system);
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      if (method != null) {
        type = getType(method);
      }
    }

    if (type == null) {
      type = PsiType.getJavaLangObject(methodCall.getManager(), methodCall.getResolveScope());
    }

    PsiType returnType = ((PsiMethod)typeParameter.getOwner()).getReturnType();
    PsiType guess = JavaPsiFacade.getInstance(parent.getProject()).getResolveHelper()
      .getSubstitutionForTypeParameter(typeParameter, returnType, type, false, PsiUtil.getLanguageLevel(parent));

    if (PsiType.NULL.equals(guess)) {
      PsiType superType = substitutor.substitute(typeParameter.getSuperTypes()[0]);
      return superType == null ? PsiType.getJavaLangObject(methodCall.getManager(), methodCall.getResolveScope()) : superType;
    }

    //The following code is the result of deep thought, do not shit it out before discussing with [ven]
    if (returnType instanceof PsiClassType && typeParameter.equals(((PsiClassType)returnType).resolve())) {
      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      PsiSubstitutor newSubstitutor = substitutor.put(typeParameter, guess);
      for (PsiClassType t : extendsTypes) {
        PsiType extendsType = newSubstitutor.substitute(t);
        if (!extendsType.isAssignableFrom(guess)) {
          if (guess.isAssignableFrom(extendsType)) {
            guess = extendsType;
            newSubstitutor = substitutor.put(typeParameter, guess);
          }
          else {
            break;
          }
        }
      }
    }

    return guess;
  }

  PsiType evaluateType(PsiExpression expr, final ReductionSystem system) {
    if (expr instanceof PsiArrayAccessExpression && !mySettings.preserveRawArrays()) {
      PsiType at = evaluateType(((PsiArrayAccessExpression)expr).getArrayExpression(), system);

      if (at instanceof PsiArrayType) {
        return ((PsiArrayType)at).getComponentType();
      }
    }
    else if (expr instanceof PsiAssignmentExpression) {
      return evaluateType(((PsiAssignmentExpression)expr).getLExpression(), system);
    }
    else if (expr instanceof PsiCallExpression) {
      PsiCallExpression call = (PsiCallExpression)expr;
      PsiMethod method = call.resolveMethod();

      if (method != null) {
        PsiClass aClass = method.getContainingClass();
        final PsiTypeParameter[] methodTypeParameters = method.getTypeParameters();
        PsiParameter[] parameters = method.getParameterList().getParameters();
        PsiExpression[] arguments = call.getArgumentList().getExpressions();
        PsiExpression qualifier =
          expr instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)expr).getMethodExpression().getQualifierExpression() : null;

        final HashSet<PsiTypeParameter> typeParameters = new HashSet<PsiTypeParameter>(Arrays.asList(methodTypeParameters));

        PsiSubstitutor qualifierSubstitutor = PsiSubstitutor.EMPTY;
        PsiSubstitutor supertypeSubstitutor = PsiSubstitutor.EMPTY;

        PsiType aType;

        if (method.isConstructor()) {
          if (expr instanceof PsiNewExpression) {
            aType = isCooked(expr) ? getType(expr) : expr.getType();
            qualifierSubstitutor = Util.resolveType(aType).getSubstitutor();
          }
          else {
            LOG.assertTrue(expr instanceof PsiMethodCallExpression); //either this(); or super();
            PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expr).getMethodExpression();
            if (PsiKeyword.THIS.equals(methodExpression.getText())) {
              aType = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createType(aClass);
            }
            else {
              LOG.assertTrue(PsiKeyword.SUPER.equals(methodExpression.getText()));
              PsiClass placeClass = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
              qualifierSubstitutor = TypeConversionUtil.getClassSubstitutor(aClass, placeClass, PsiSubstitutor.EMPTY);
              aType = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createType(aClass, qualifierSubstitutor);
            }
          }
        }
        else {
          aType = getType(method);
        }

        if (qualifier != null) {
          PsiType qualifierType = evaluateType(qualifier, system);
          PsiClassType.ClassResolveResult result = Util.resolveType(qualifierType);

          if (result.getElement() != null) {
            PsiClass qualifierClass = result.getElement();

            qualifierSubstitutor = TypeConversionUtil.getClassSubstitutor(aClass, qualifierClass, result.getSubstitutor());

            if (qualifierSubstitutor != null) {
              aType = qualifierSubstitutor.substitute(aType);
            }
          }
        }

        final HashMap<PsiTypeParameter, PsiType> mapping = new HashMap<PsiTypeParameter, PsiType>();

        for (int i = 0; i < Math.min(parameters.length, arguments.length); i++) {
          PsiType argumentType = evaluateType(arguments[i], system);

          PsiType parmType;

          if (isCooked(parameters[i])) {
            parmType = getType(parameters[i]);
            system.addSubtypeConstraint(argumentType, parmType);
          }
          else {
            parmType = parameters[i].getType();
            if (qualifierSubstitutor != null) {
              parmType = qualifierSubstitutor.substitute(parmType);
            }

            if (!Util.bindsTypeVariables(parmType) && !Util.bindsTypeParameters(parmType, typeParameters)) {
              parmType = Util.banalize(parmType);
            }

            PsiType theType = new Object() {
              PsiType introduceAdditionalTypeVariables(PsiType type, PsiSubstitutor qualifier, PsiSubstitutor supertype) {
                int level = type.getArrayDimensions();
                PsiClassType.ClassResolveResult result = Util.resolveType(type);
                PsiClass aClass = result.getElement();

                if (aClass != null) {
                  if (aClass instanceof PsiTypeParameter) {
                    PsiTypeParameter tp = (PsiTypeParameter)aClass;
                    PsiClassType[] extypes = tp.getExtendsListTypes();

                    PsiType pv = mapping.get(tp);

                    if (pv == null) {
                      pv = myTypeVariableFactory.create();
                      mapping.put(tp, pv);
                    }

                    for (PsiClassType ext : extypes) {
                      PsiType extype = qualifier.substitute(new Object() {
                        public PsiType substitute(PsiType ext) {
                          PsiClassType.ClassResolveResult result = Util.resolveType(ext);
                          PsiClass aClass = result.getElement();

                          if (aClass != null) {
                            if (aClass instanceof PsiTypeParameter) {
                              PsiType type = mapping.get(aClass);

                              if (type != null) {
                                return type;
                              }

                              return ext;
                            }

                            PsiSubstitutor aSubst = result.getSubstitutor();
                            PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

                            for (PsiTypeParameter parm : aSubst.getSubstitutionMap().keySet()) {
                              PsiType type = aSubst.substitute(parm);

                              if (type != null) {
                                if (type instanceof PsiWildcardType) {
                                  PsiWildcardType wildcard = (PsiWildcardType)type;
                                  PsiType bound = wildcard.getBound();
                                  if (bound != null) {
                                    PsiManager manager = parm.getManager();
                                    type = wildcard.isExtends()
                                           ? PsiWildcardType.createExtends(manager, substitute(bound))
                                           : PsiWildcardType.createSuper(manager, substitute(bound));
                                  }
                                }
                                else {
                                  type = substitute(type);
                                }
                              }

                              theSubst = theSubst.put(parm, type);
                            }

                            return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory()
                              .createType(aClass, theSubst);
                          }

                          return ext;
                        }
                      }.substitute(ext));
                      system.addSubtypeConstraint(pv, extype);
                    }

                    return Util.createArrayType(pv, level);
                  }

                  Map<PsiTypeParameter, PsiType> substitutionMap = result.getSubstitutor().getSubstitutionMap();

                  PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

                  for (PsiTypeParameter p : substitutionMap.keySet()) {
                    PsiType pType = substitutionMap.get(p);

                    if (pType instanceof PsiWildcardType) {
                      PsiWildcardType wildcard = (PsiWildcardType)pType;
                      PsiType theBound = wildcard.getBound();

                      if (theBound != null) {
                        PsiType bound = qualifier.substitute(supertype.substitute(theBound));

                        if (Util.bindsTypeVariables(bound)) {
                          PsiType var = myTypeVariableFactory.create();

                          if (wildcard.isExtends()) {
                            system.addSubtypeConstraint(var, bound);
                          }
                          else {
                            system.addSubtypeConstraint(bound, var);
                          }

                          theSubst = theSubst.put(p, var);
                        }
                        else if (Util.bindsTypeParameters(bound, typeParameters)) {
                          PsiType var = myTypeVariableFactory.create();
                          PsiSubstitutor subst = PsiSubstitutor.EMPTY;

                          for (PsiTypeParameter aTypeParm : methodTypeParameters) {
                            PsiType parmVar = mapping.get(aTypeParm);

                            if (parmVar == null) {
                              parmVar = myTypeVariableFactory.create();
                              mapping.put(aTypeParm, parmVar);
                            }

                            subst = subst.put(aTypeParm, parmVar);
                          }

                          PsiType bnd = subst.substitute(bound);

                          if (wildcard.isExtends()) {
                            system.addSubtypeConstraint(bnd, var);
                          }
                          else {
                            system.addSubtypeConstraint(var, bnd);
                          }

                          theSubst = theSubst.put(p, var);
                        }
                        else {
                          theSubst = theSubst.put(p, pType);
                        }
                      }
                      else {
                        theSubst = theSubst.put(p, pType);
                      }
                    }
                    else if (pType != null) {
                      theSubst = theSubst.put(p, introduceAdditionalTypeVariables(pType, qualifier, supertype));
                    }
                  }

                  return Util.createArrayType(JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass, theSubst), level);
                }

                return Util.createArrayType(type, level);
              }
            }.introduceAdditionalTypeVariables(parmType, qualifierSubstitutor, supertypeSubstitutor);

            system.addSubtypeConstraint(argumentType, theType);
          }
        }

        PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

        for (PsiTypeParameter parm : mapping.keySet()) {
          PsiType type = mapping.get(parm);

          theSubst = theSubst.put(parm, type);
        }

        for (PsiTypeParameter typeParam : methodTypeParameters) {
          PsiType inferred = inferTypeForMethodTypeParameter(typeParam, parameters, arguments, theSubst, expr, system);
          theSubst = theSubst.put(typeParam, inferred);
        }

        return theSubst.substitute(aType);
      }
    }
    else if (expr instanceof PsiParenthesizedExpression) {
      return evaluateType(((PsiParenthesizedExpression)expr).getExpression(), system);
    }
    else if (expr instanceof PsiConditionalExpression) {
      return evaluateType(((PsiConditionalExpression)expr).getThenExpression(), system);
    }
    else if (expr instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)expr;
      PsiExpression qualifier = ref.getQualifierExpression();

      if (qualifier == null) {
        return getType(ref.resolve());
      }
      else {
        PsiType qualifierType = evaluateType(qualifier, system);
        PsiElement element = ref.resolve();

        PsiClassType.ClassResolveResult result = Util.resolveType(qualifierType);

        if (result.getElement() != null) {
          PsiClass aClass = result.getElement();
          PsiSubstitutor aSubst = result.getSubstitutor();

          if (element instanceof PsiField) {
            PsiField field = (PsiField)element;
            PsiType fieldType = getType(field);
            PsiClass superClass = field.getContainingClass();

            PsiType aType = fieldType;

            if (!aClass.equals(superClass) && field.isPhysical()) {
              aType = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY).substitute(aType);
            }

            return aSubst.substitute(aType);
          }
        }
        else if (element != null) {
          return getType(element);
        }
      }
    }

    return getType(expr);
  }


  private void addUsage(final ReductionSystem system, PsiElement element) {

    if (element instanceof PsiVariable) {
      PsiExpression initializer = ((PsiVariable)element).getInitializer();

      if (initializer != null) {
        PsiExpression core = PsiUtil.deparenthesizeExpression(initializer);

        if (core instanceof PsiArrayInitializerExpression) {
          PsiExpression[] inits = ((PsiArrayInitializerExpression)core).getInitializers();
          PsiType type = getType(element);

          for (PsiExpression init : inits) {
            system.addSubtypeConstraint(evaluateType(init, system).createArrayType(), type);
          }
        }
        else if (core instanceof PsiNewExpression) {
          PsiArrayInitializerExpression init = ((PsiNewExpression)core).getArrayInitializer();

          if (init != null) {
            PsiExpression[] inits = init.getInitializers();
            PsiType type = getType(element);

            for (PsiExpression init1 : inits) {
              system.addSubtypeConstraint(evaluateType(init1, system).createArrayType(), type);
            }
          }

          system.addSubtypeConstraint(evaluateType(core, system), getType(element));
        }
        else {
          system.addSubtypeConstraint(evaluateType(core, system), getType(element));
        }
      }

      if (element instanceof PsiParameter) {
        PsiParameter parameter = (PsiParameter)element;
        PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)declarationScope;
          PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(myManager.getProject());
          SearchScope scope = getScope(helper, method);

          for (PsiReference ref : ReferencesSearch.search(method, scope, true)) {
            PsiElement elt = ref.getElement();

            if (elt != null) {
              PsiCallExpression call = PsiTreeUtil.getParentOfType(elt, PsiCallExpression.class);

              if (call != null) {
                PsiExpressionList argList = call.getArgumentList();
                if (argList != null) {
                  PsiExpression[] args = argList.getExpressions();
                  int index = method.getParameterList().getParameterIndex(parameter);
                  if (index < args.length) {
                    system.addSubtypeConstraint(evaluateType(args[index], system), myTypes.get(element));
                  }
                }
              }
            }
          }
        } else if (declarationScope instanceof PsiForeachStatement) {
          addForEachConstraint(system, (PsiForeachStatement)declarationScope);
        }
      }
      return;
    }
    else if (element instanceof PsiMethod) {
      final PsiType reType = getType(element);

      element.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReturnStatement(PsiReturnStatement statement) {
          super.visitReturnStatement(statement);

          PsiExpression retExpr = statement.getReturnValue();

          if (retExpr != null) {
            system.addSubtypeConstraint(evaluateType(retExpr, system), reType);
          }
        }
      });

      return;
    }

    PsiElement root = PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiField.class);

    if (root != null) {
      PsiAnchor anchor = PsiAnchor.create(root);

      if (!myVisitedConstructions.contains(anchor)) {
        root.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);

            system
              .addSubtypeConstraint(evaluateType(expression.getRExpression(), system), evaluateType(expression.getLExpression(), system));
          }

          @Override public void visitConditionalExpression(PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);

            system.addSubtypeConstraint(evaluateType(expression.getThenExpression(), system),
                                        evaluateType(expression.getElseExpression(), system));
            system.addSubtypeConstraint(evaluateType(expression.getElseExpression(), system),
                                        evaluateType(expression.getThenExpression(), system));
          }

          @Override public void visitCallExpression(PsiCallExpression expression) {
            super.visitCallExpression(expression);
            evaluateType(expression, system);
          }

          @Override public void visitReturnStatement(PsiReturnStatement statement) {
            super.visitReturnStatement(statement);

            PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);

            if (method != null) {
              system.addSubtypeConstraint(evaluateType(statement.getReturnValue(), system), getType(method));
            }
          }

          @Override public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);

            PsiType operandType = evaluateType(expression.getOperand(), system);
            PsiType castType = evaluateType(expression, system);
            if (operandType == null || castType == null) return;

            if (Util.bindsTypeVariables(operandType)) {
              system.addCast(expression, operandType);
            }

            if (operandType.getDeepComponentType() instanceof PsiTypeVariable ||
                castType.getDeepComponentType() instanceof PsiTypeVariable) {
              system.addSubtypeConstraint(operandType, castType);
            }
            else {
              PsiClassType.ClassResolveResult operandResult = Util.resolveType(operandType);
              PsiClassType.ClassResolveResult castResult = Util.resolveType(castType);

              PsiClass operandClass = operandResult.getElement();
              PsiClass castClass = castResult.getElement();

              if (operandClass != null && castClass != null) {
                if (InheritanceUtil.isInheritorOrSelf(operandClass, castClass, true)) {
                  system.addSubtypeConstraint(operandType, castType);
                }
              }
            }
          }

          @Override public void visitVariable(PsiVariable variable) {
            super.visitVariable(variable);

            PsiExpression init = variable.getInitializer();

            if (init != null) {
              system.addSubtypeConstraint(evaluateType(init, system), getType(variable));
            }
          }

          @Override public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);

            PsiArrayInitializerExpression init = expression.getArrayInitializer();

            if (init != null) {
              PsiExpression[] inits = init.getInitializers();
              PsiType type = getType(expression);

              for (PsiExpression init1 : inits) {
                system.addSubtypeConstraint(evaluateType(init1, system).createArrayType(), type);
              }
            }
          }

          @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
            PsiExpression qualifierExpression = expression.getQualifierExpression();

            if (qualifierExpression != null) {
              qualifierExpression.accept(this);
            }
          }
        });

        myVisitedConstructions.add(anchor);
      }
    }
  }

  private static SearchScope getScope(PsiSearchHelper helper, PsiElement element) {
    SearchScope scope = helper.getUseScope(element);
    if (scope instanceof GlobalSearchScope) {
      scope =
        GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)scope, JavaFileType.INSTANCE/*, StdFileTypes.JSP, StdFileTypes.JSPX*/);
    }
    return scope;
  }

  PsiType replaceWildCards(PsiType type, ReductionSystem system, PsiSubstitutor definedSubst) {
    if (type instanceof PsiWildcardType) {
      PsiWildcardType wildcard = (PsiWildcardType)type;
      PsiType var = myTypeVariableFactory.create();
      PsiType bound = wildcard.getBound();

      if (bound != null) {
        if (wildcard.isExtends()) {
          system.addSubtypeConstraint(Util.banalize(definedSubst.substitute(replaceWildCards(bound, system, definedSubst))), var);
        }
        else {
          system.addSubtypeConstraint(var, Util.banalize(definedSubst.substitute(replaceWildCards(bound, system, definedSubst))));
        }
      }

      return var;
    }
    else if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = Util.resolveType(type);
      PsiClass aClass = result.getElement();
      PsiSubstitutor aSubst = result.getSubstitutor();

      if (aClass != null) {
        PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

        for (PsiTypeParameter p : aSubst.getSubstitutionMap().keySet()) {
          theSubst = theSubst.put(p, replaceWildCards(aSubst.substitute(p), system, definedSubst));
        }

        return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass, theSubst);
      }
    }

    return type;
  }

  private void addBoundConstraintsImpl(PsiType defined, PsiType type, ReductionSystem system) {
    PsiClassType.ClassResolveResult resultDefined = Util.resolveType(defined);
    PsiClassType.ClassResolveResult resultType = Util.resolveType(type);
    PsiClass definedClass = resultDefined.getElement();

    if (definedClass == null || !definedClass.equals(resultType.getElement())) {
      return;
    }

    PsiSubstitutor definedSubst = resultDefined.getSubstitutor();
    PsiSubstitutor typeSubst = resultType.getSubstitutor();

    for (PsiTypeParameter parameter : definedSubst.getSubstitutionMap().keySet()) {
      PsiClassType[] extendsList = parameter.getExtendsList().getReferencedTypes();
      PsiType definedType = definedSubst.substitute(parameter);

      if (definedType instanceof PsiTypeVariable) {
        for (PsiType extendsType : extendsList) {
          extendsType = replaceWildCards(extendsType, system, definedSubst);

          system.addSubtypeConstraint(definedType, Util.banalize(definedSubst.substitute(extendsType)));
        }
      }
      else {
        addBoundConstraintsImpl(definedType, typeSubst.substitute(parameter), system);
      }
    }
  }


  private void addBoundConstraints(ReductionSystem system, PsiType definedType, PsiElement element) {
    PsiType elemenType = Util.getType(element);

    if (elemenType != null) {
      addBoundConstraintsImpl(definedType, elemenType, system);

      if (mySettings.cookObjects() && elemenType.getCanonicalText().equals(CommonClassNames.JAVA_LANG_OBJECT)) {
        system.addSubtypeConstraint(definedType, elemenType);
      }
    }
  }

  public ReductionSystem build(PsiElement... scopes) {
    return build(collect(scopes));
  }

  public ReductionSystem build(HashSet<PsiElement> victims) {
    PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(myManager.getProject());

    ReductionSystem system = new ReductionSystem(myProject, victims, myTypes, myTypeVariableFactory, mySettings);

    for (PsiElement element : victims) {
      if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) {
        if (!verifyMethod(element, victims, helper)) {
          continue;
        }
      }
      else if (element instanceof PsiMethod) {
        if (!verifyMethod(element, victims, helper)) {
          continue;
        }
      }
    }

    for (PsiElement element : victims) {
      PsiType definedType;
      if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) {
        PsiParameter p = myParameters.get(element);

        if (p != null) {
          setType(element, definedType = defineType(p));
        }
        else {
          continue;
        }
      }
      else if (element instanceof PsiMethod) {
        PsiMethod m = myMethods.get(element);

        if (m != null) {
          system.addSubtypeConstraint(defineType(element), definedType = defineType(m));
        }
        else {
          continue;
        }
      }
      else {
        definedType = defineType(element);
      }

      addBoundConstraints(system, definedType, element);
    }

    for (PsiElement element : victims) {
      if (element instanceof PsiParameter) {
        PsiElement scope = ((PsiParameter)element).getDeclarationScope();
        if (scope instanceof PsiMethod) {
          PsiParameter p = myParameters.get(element);

          if (p == null) continue;
        }
        /*else if (scope instanceof PsiForeachStatement) {
          addForEachConstraint(system, (PsiForeachStatement)scope);
        }*/
        else if (element instanceof PsiMethod) {
          PsiMethod m = myMethods.get(element);

          if (m == null) continue;
        }
      }
      else if (element instanceof PsiMethod) {
        PsiMethod m = myMethods.get(element);

        if (m == null) continue;
      }

      addUsage(system, element);

      if (!(element instanceof PsiExpression)) {

        for (PsiReference ref : ReferencesSearch.search(element, getScope(helper, element), true)) {
          PsiElement elt = ref.getElement();

          if (elt != null) {
            addUsage(system, elt);
          }
        }
      }
    }

    return system;
  }

  private void addForEachConstraint(ReductionSystem system, PsiForeachStatement statement) {
    PsiType paramType = getType(statement.getIterationParameter());
    PsiExpression value = statement.getIteratedValue();
    if (value != null) {
      PsiType type = evaluateType(value, system);
      if (type instanceof PsiClassType) {
        PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
        PsiClass clazz = resolveResult.getElement();
        if (clazz != null) {
          PsiClass iterableClass =
            JavaPsiFacade.getInstance(clazz.getProject()).findClass(CommonClassNames.JAVA_LANG_ITERABLE, clazz.getResolveScope());
          if (iterableClass != null) {
            PsiTypeParameter[] typeParameters = iterableClass.getTypeParameters();
            if (typeParameters.length == 1) {
              PsiSubstitutor substitutor =
                TypeConversionUtil.getClassSubstitutor(iterableClass, clazz, resolveResult.getSubstitutor());
              if (substitutor != null) {
                PsiType componentType = substitutor.substitute(typeParameters[0]);
                system.addSubtypeConstraint(componentType, paramType);
              }
            }
          }
        }
      }
      else if (type instanceof PsiArrayType) {
        system.addSubtypeConstraint(((PsiArrayType)type).getComponentType(), paramType);
      }
    }
  }
}
