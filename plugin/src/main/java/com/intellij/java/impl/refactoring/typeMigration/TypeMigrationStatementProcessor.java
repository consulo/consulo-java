/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration;

import com.intellij.java.analysis.impl.psi.controlFlow.DefUseUtil;
import com.intellij.java.impl.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.java.impl.codeInsight.intention.impl.SplitDeclarationAction;
import com.intellij.java.impl.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.java.language.impl.psi.impl.PsiSubstitutorImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.util.function.CommonProcessors;
import consulo.language.ast.IElementType;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author anna
 */
class TypeMigrationStatementProcessor extends JavaRecursiveElementVisitor {
  private final PsiElement myStatement;
  private final TypeMigrationLabeler myLabeler;
  private static final Logger LOG = Logger.getInstance(TypeMigrationStatementProcessor.class);
  private final TypeEvaluator myTypeEvaluator;

  public TypeMigrationStatementProcessor(PsiElement expression, TypeMigrationLabeler labeler) {
    myStatement = expression;
    myLabeler = labeler;
    myTypeEvaluator = myLabeler.getTypeEvaluator();
  }

  @Override
  public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    super.visitAssignmentExpression(expression);

    PsiExpression lExpression = expression.getLExpression();
    TypeView left = new TypeView(lExpression);

    PsiExpression rExpression = expression.getRExpression();
    if (rExpression == null) {
      return;
    }
    TypeView right = new TypeView(rExpression);

    IElementType sign = expression.getOperationTokenType();
    PsiType ltype = left.getType();
    PsiType rtype = right.getType();
    if (ltype == null || rtype == null) {
      return;
    }

    if (sign != JavaTokenType.EQ) {
      IElementType binaryOperator = TypeConversionUtil.convertEQtoOperation(sign);
      if (!TypeConversionUtil.isBinaryOperatorApplicable(binaryOperator, ltype, rtype, false)) {
        if (left.isChanged()) {
          findConversionOrFail(expression, lExpression, left.getTypePair());
        }
        if (right.isChanged()) {
          findConversionOrFail(expression, rExpression, right.getTypePair());
        }
        return;
      }
    }

    switch (TypeInfection.getInfection(left, right)) {
      case TypeInfection.NONE_INFECTED:
        break;

      case TypeInfection.LEFT_INFECTED:
        myLabeler.migrateExpressionType(rExpression, ltype, myStatement, TypeConversionUtil.isAssignable(ltype, rtype) && !isSetter(expression), true);
        break;

      case TypeInfection.RIGHT_INFECTED:
        if (lExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression) lExpression).resolve() instanceof PsiLocalVariable && !canBeVariableType(rtype)) {
          tryToRemoveLocalVariableAssignment((PsiLocalVariable) ((PsiReferenceExpression) lExpression).resolve(), rExpression, rtype);
        } else {
          myLabeler.migrateExpressionType(lExpression, rtype, myStatement, TypeConversionUtil.isAssignable(ltype, rtype), false);
        }
        break;

      case TypeInfection.BOTH_INFECTED:
        addTypeUsage(lExpression);
        addTypeUsage(rExpression);
        break;

      default:
        LOG.error("Must not happen.");
    }
  }

  @Override
  public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
    super.visitArrayAccessExpression(expression);
    PsiExpression indexExpression = expression.getIndexExpression();
    if (indexExpression != null) {
      checkIndexExpression(indexExpression);
    }
    TypeView typeView = new TypeView(expression.getArrayExpression());
    if (typeView.isChanged() && typeView.getType() instanceof PsiClassType) {
      TypeConversionDescriptorBase conversion = myLabeler.getRules().findConversion(typeView.getTypePair().first, typeView.getType(), null, expression, false, myLabeler);

      if (conversion == null) {
        myLabeler.markFailedConversion(typeView.getTypePair(), expression);
      } else {
        myLabeler.setConversionMapping(expression, conversion);
        myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), myTypeEvaluator.evaluateType(expression));
      }

    }
  }

  @Override
  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    PsiExpression caseValue = statement.getCaseValue();
    if (caseValue != null) {
      TypeView typeView = new TypeView(caseValue);
      if (typeView.isChanged()) {
        PsiSwitchStatement switchStatement = statement.getEnclosingSwitchStatement();
        if (switchStatement != null) {
          PsiExpression expression = switchStatement.getExpression();
          myLabeler.migrateExpressionType(expression, typeView.getType(), myStatement, false, false);
        }
      }
    }
  }

  @Override
  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    PsiTypeElement typeElement = expression.getCheckType();
    if (typeElement != null) {
      PsiExpression consideredExpression = expression.getOperand();
      PsiType migrationType = myTypeEvaluator.evaluateType(consideredExpression);
      PsiType fixedType = typeElement.getType();
      if (migrationType != null && !TypeConversionUtil.isAssignable(migrationType, fixedType)) {
        myLabeler.markFailedConversion(Pair.create(fixedType, migrationType), consideredExpression);
      }
    }
  }

  @Override
  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    super.visitTypeCastExpression(expression);
    PsiTypeElement typeElement = expression.getCastType();
    if (typeElement != null) {
      PsiType fixedType = typeElement.getType();
      PsiType migrationType = myTypeEvaluator.evaluateType(expression.getOperand());
      if (migrationType != null && !TypeConversionUtil.areTypesConvertible(migrationType, fixedType)) {
        myLabeler.markFailedConversion(Pair.create(fixedType, migrationType), expression);
      }
    }
  }

  @Override
  public void visitVariable(PsiVariable variable) {
    super.visitVariable(variable);

    PsiExpression initializer = variable.getInitializer();

    if (initializer != null && initializer.getType() != null) {
      processVariable(variable, initializer, null, null, null, false);
    }
  }

  @Override
  public void visitReturnStatement(PsiReturnStatement statement) { // has to change method return type corresponding to new value type
    super.visitReturnStatement(statement);

    PsiElement method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
    PsiExpression value = statement.getReturnValue();

    if (method != null && value != null) {
      if (method instanceof PsiLambdaExpression) {
        //todo [IDEA-133097]
        return;
      }
      PsiType returnType = ((PsiMethod) method).getReturnType();
      PsiType valueType = myTypeEvaluator.evaluateType(value);
      if (returnType != null && valueType != null) {
        if ((isGetter(value, method) || !TypeConversionUtil.isAssignable(returnType, valueType)) && returnType.equals(myTypeEvaluator.getType(method)) && !myLabeler.addMigrationRoot(method,
            valueType, myStatement, false, true)) {
          myLabeler.convertExpression(value, valueType, returnType, false);
        }
      }
    }
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    PsiExpression qualifierExpression = expression.getQualifierExpression();

    if (qualifierExpression != null && qualifierExpression.isPhysical()) {
      qualifierExpression.accept(this);

      TypeView qualifierView = new TypeView(qualifierExpression);

      if (qualifierView.isChanged()) {
        PsiMember member = (PsiMember) expression.advancedResolve(false).getElement();
        if (member == null) {
          return;
        }
        Pair<PsiType, PsiType> typePair = qualifierView.getTypePair();

        TypeConversionDescriptorBase conversion = myLabeler.getRules().findConversion(typePair.getFirst(), typePair.getSecond(), member, expression, false, myLabeler);

        if (conversion == null) {
          myLabeler.markFailedConversion(typePair, qualifierExpression);
        } else {
          PsiElement parent = Util.getEssentialParent(expression);
          PsiType type = conversion.conversionType();
          if (parent instanceof PsiMethodCallExpression) {
            myLabeler.setConversionMapping((PsiMethodCallExpression) parent, conversion);
            myTypeEvaluator.setType(new TypeMigrationUsageInfo(parent), type != null ? type : myTypeEvaluator.evaluateType((PsiExpression) parent));
          } else {
            myLabeler.setConversionMapping(expression, conversion);
            myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), type != null ? type : myTypeEvaluator.evaluateType(expression));
          }
        }
      }
    }
  }

  @Override
  public void visitIfStatement(PsiIfStatement statement) {
    super.visitIfStatement(statement);
    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      TypeView view = new TypeView(condition);
      if (view.isChanged()) { //means that boolean condition becomes non-boolean
        findConversionOrFail(condition, condition, view.getTypePair());
      }
    }
  }

  @Override
  public void visitForeachStatement(PsiForeachStatement statement) {
    super.visitForeachStatement(statement);
    PsiExpression value = statement.getIteratedValue();
    PsiParameter psiParameter = statement.getIterationParameter();
    if (value != null) {
      TypeView typeView = new TypeView(value);
      PsiType psiType = typeView.getType();
      if (psiType instanceof PsiArrayType) {
        psiType = ((PsiArrayType) psiType).getComponentType();
      } else if (psiType instanceof PsiClassType) {
        PsiClassType.ClassResolveResult resolveResult = ((PsiClassType) psiType).resolveGenerics();
        PsiClass psiClass = resolveResult.getElement();
        PsiType targetTypeParameter = getTargetTypeParameter(psiClass, value, typeView);
        if (targetTypeParameter == null) {
          return;
        }
        psiType = resolveResult.getSubstitutor().substitute(targetTypeParameter);
        if (psiType instanceof PsiWildcardType) {
          psiType = ((PsiWildcardType) psiType).getExtendsBound();
        }
      } else {
        return;
      }
      TypeView left = new TypeView(psiParameter, null, null);
      if (TypeInfection.getInfection(left, typeView) == TypeInfection.LEFT_INFECTED) {
        PsiType iterableType;
        PsiType typeViewType = typeView.getType();
        if (typeViewType instanceof PsiArrayType) {
          iterableType = left.getType().createArrayType();
        } else {
          PsiClass iterableClass = PsiUtil.resolveClassInType(typeViewType);
          LOG.assertTrue(iterableClass != null);

          PsiType targetType = getTargetTypeParameter(iterableClass, value, typeView);
          PsiClass typeParam = PsiUtil.resolveClassInClassTypeOnly(targetType);
          if (!(typeParam instanceof PsiTypeParameter)) {
            return;
          }

          Map<PsiTypeParameter, PsiType> substMap = Collections.singletonMap(((PsiTypeParameter) typeParam), left.getType());

          PsiElementFactory factory = JavaPsiFacade.getElementFactory(iterableClass.getProject());
          iterableType = factory.createType(iterableClass, factory.createSubstitutor(substMap));
        }
        myLabeler.migrateExpressionType(value, iterableType, myStatement, TypeConversionUtil.isAssignable(iterableType, typeViewType), true);
      } else {
        processVariable(psiParameter, value, psiType, null, null, false);
      }
    }
  }

  private PsiType getTargetTypeParameter(PsiClass iterableClass, PsiExpression value, TypeView typeView) {
    Project project = iterableClass.getProject();
    PsiClass itClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_ITERABLE, GlobalSearchScope.allScope(project));
    if (itClass == null) {
      return null;
    }

    if (!InheritanceUtil.isInheritorOrSelf(iterableClass, itClass, true)) {
      findConversionOrFail(value, value, typeView.getTypePair());
      return null;
    }

    PsiSubstitutor aSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(itClass, iterableClass, PsiSubstitutor.EMPTY);

    return aSubstitutor.substitute(itClass.getTypeParameters()[0]);
  }

  @Override
  public void visitNewExpression(PsiNewExpression expression) {
    super.visitNewExpression(expression);
    PsiExpression[] dimensions = expression.getArrayDimensions();
    for (PsiExpression dimension : dimensions) {
      checkIndexExpression(dimension);
    }
    PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
    if (arrayInitializer != null) {
      processArrayInitializer(arrayInitializer, expression);
    }
  }

  @Override
  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    super.visitArrayInitializerExpression(expression);
    processArrayInitializer(expression, expression);
  }

  @Override
  public void visitUnaryExpression(PsiUnaryExpression expression) {
    super.visitUnaryExpression(expression);
    TypeView typeView = new TypeView(expression);
    if (typeView.isChanged()) {
      if (!TypeConversionUtil.isUnaryOperatorApplicable(expression.getOperationSign(), typeView.getType())) {
        findConversionOrFail(expression, expression, typeView.getTypePair());
      }
    }
  }

  private void findConversionOrFail(PsiExpression expression, PsiExpression toFail, Pair<PsiType, PsiType> typePair) {
    TypeConversionDescriptorBase conversion = myLabeler.getRules().findConversion(typePair.getFirst(), typePair.getSecond(), null, expression, myLabeler);
    if (conversion == null) {
      myLabeler.markFailedConversion(typePair, toFail);
    } else {
      myLabeler.setConversionMapping(expression, conversion);
      PsiType psiType = myTypeEvaluator.evaluateType(expression);
      LOG.assertTrue(psiType != null, expression);
      myTypeEvaluator.setType(new TypeMigrationUsageInfo(expression), psiType);
    }
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    super.visitPolyadicExpression(expression);
    PsiExpression[] operands = expression.getOperands();
    if (operands.length == 0) {
      return;
    }
    IElementType operationTokenType = expression.getOperationTokenType();

    PsiExpression lOperand = operands[0];
    TypeView left = new TypeView(lOperand);
    for (int i = 1; i < operands.length; i++) {
      PsiExpression rOperand = operands[i];
      if (rOperand == null) {
        return;
      }
      TypeView right = new TypeView(rOperand);
      if (tryFindConversionIfOperandIsNull(left, right, rOperand)) {
        continue;
      }
      if (tryFindConversionIfOperandIsNull(right, left, lOperand)) {
        continue;
      }
      if (!TypeConversionUtil.isBinaryOperatorApplicable(operationTokenType, left.getType(), right.getType(), false)) {
        if (left.isChanged()) {
          findConversionOrFail(lOperand, lOperand, left.getTypePair());
        }
        if (right.isChanged()) {
          findConversionOrFail(rOperand, rOperand, right.getTypePair());
        }
      }
      lOperand = rOperand;
      left = right;
    }
  }

  protected boolean tryFindConversionIfOperandIsNull(TypeView nullCandidate, TypeView comparingType, PsiExpression comparingExpr) {
    if (nullCandidate.getType() == PsiType.NULL && comparingType.isChanged()) {
      Pair<PsiType, PsiType> typePair = comparingType.getTypePair();
      TypeConversionDescriptorBase conversion = myLabeler.getRules().findConversion(typePair.getFirst(), typePair.getSecond(), null, comparingExpr, false, myLabeler);
      if (conversion != null) {
        myLabeler.setConversionMapping(comparingExpr, conversion);
      }
      return true;
    }
    return false;
  }

  private void processArrayInitializer(PsiArrayInitializerExpression expression, PsiExpression parentExpression) {
    PsiExpression[] initializers = expression.getInitializers();
    PsiType migrationType = null;
    for (PsiExpression initializer : initializers) {
      TypeView typeView = new TypeView(initializer);
      if (typeView.isChanged()) {
        PsiType type = typeView.getType();
        if (migrationType == null || !TypeConversionUtil.isAssignable(migrationType, type)) {
          if (migrationType != null && !TypeConversionUtil.isAssignable(type, migrationType)) {
            myLabeler.markFailedConversion(Pair.create(parentExpression.getType(), type), parentExpression);
            return;
          }
          migrationType = type;
        }
      }
    }
    PsiType exprType = expression.getType();
    if (migrationType != null && exprType instanceof PsiArrayType) {
      boolean alreadyProcessed = TypeConversionUtil.isAssignable(((PsiArrayType) exprType).getComponentType(), migrationType);
      myLabeler.migrateExpressionType(parentExpression, alreadyProcessed ? exprType : migrationType.createArrayType(), expression, alreadyProcessed, true);
    }
  }

  private void checkIndexExpression(PsiExpression indexExpression) {
    PsiType indexType = myTypeEvaluator.evaluateType(indexExpression);
    if (indexType != null && !TypeConversionUtil.isAssignable(PsiType.INT, indexType)) {
      myLabeler.markFailedConversion(Pair.create(indexExpression.getType(), indexType), indexExpression);
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression methodCallExpression) {
    super.visitMethodCallExpression(methodCallExpression);
    JavaResolveResult resolveResult = methodCallExpression.resolveMethodGenerics();
    PsiElement method = resolveResult.getElement();
    if (method instanceof PsiMethod) {
      if (migrateEqualsMethod(methodCallExpression, (PsiMethod) method)) {
        return;
      }
      PsiExpression[] psiExpressions = methodCallExpression.getArgumentList().getExpressions();
      PsiParameter[] originalParams = ((PsiMethod) method).getParameterList().getParameters();
      PsiSubstitutor evalSubstitutor = myTypeEvaluator.createMethodSubstitution(originalParams, psiExpressions, (PsiMethod) method, methodCallExpression);
      for (int i = 0; i < psiExpressions.length; i++) {
        PsiParameter originalParameter;
        if (originalParams.length <= i) {
          if (originalParams.length > 0 && originalParams[originalParams.length - 1].isVarArgs()) {
            originalParameter = originalParams[originalParams.length - 1];
          } else {
            continue;
          }
        } else {
          originalParameter = originalParams[i];
        }
        processVariable(originalParameter, psiExpressions[i], null, resolveResult.getSubstitutor(), evalSubstitutor, true);
      }
      PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
      if (qualifier != null && qualifier.isPhysical() && !new TypeView(qualifier).isChanged()) { //substitute property otherwise
        PsiType qualifierType = qualifier.getType();
        if (qualifierType instanceof PsiClassType) {
          PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType) qualifierType).resolveGenerics();
          PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myStatement.getProject());
          PsiType migrationType = elementFactory.createType(classResolveResult.getElement(), composeIfNotAssignable(classResolveResult.getSubstitutor(), evalSubstitutor));
          myLabeler.migrateExpressionType(qualifier, migrationType, myStatement, migrationType.equals(qualifierType), true);
        }
      }
    }
  }

  private boolean migrateEqualsMethod(PsiMethodCallExpression methodCallExpression, PsiMethod method) {
    PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
    if (qualifier == null) {
      return false;
    }
    TypeView qualifierTypeView = new TypeView(qualifier);
    if (!qualifierTypeView.isChanged()) {
      return false;
    }
    if (method.getName().equals("equals") && method.getParameterList().getParametersCount() == 1) {
      PsiParameter parameter = method.getParameterList().getParameters()[0];
      if (parameter.getType().equals(PsiType.getJavaLangObject(methodCallExpression.getManager(), methodCallExpression.getResolveScope()))) {
        PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
        if (expressions.length != 1) {
          return false;
        }
        TypeView argumentTypeView = new TypeView(expressions[0]);
        PsiType argumentType = argumentTypeView.getType();
        if (!argumentTypeView.isChanged() && qualifierTypeView.getTypePair().getFirst().equals(argumentType)) {
          PsiType migrationType = qualifierTypeView.getType();
          myLabeler.migrateExpressionType(expressions[0], migrationType, methodCallExpression, TypeConversionUtil.isAssignable(migrationType, argumentType), true);
          return true;
        }
      }
    }
    return false;
  }

  private void processVariable(PsiVariable variable,
                               PsiExpression value,
                               PsiType migrationType,
                               PsiSubstitutor varSubstitutor,
                               PsiSubstitutor evalSubstitutor,
                               boolean isCovariantPosition) {
    TypeView right = new TypeView(value);
    TypeView left = new TypeView(variable, varSubstitutor, evalSubstitutor);
    PsiType declarationType = left.getType();

    switch (TypeInfection.getInfection(left, right)) {
      case TypeInfection.NONE_INFECTED:
        break;

      case TypeInfection.LEFT_INFECTED:
        PsiType valueType = right.getType();
        if (valueType != null && declarationType != null) {
          myLabeler.migrateExpressionType(value, adjustMigrationTypeIfGenericArrayCreation(declarationType, value), myStatement, left.isVarArgs() ? isVarargAssignable(left, right) :
              TypeConversionUtil.isAssignable(declarationType, valueType), true);
        }
        break;

      case TypeInfection.RIGHT_INFECTED:
        PsiType psiType = migrationType != null ? migrationType : right.getType();
        if (psiType != null) {
          if (canBeVariableType(psiType)) {
            if (declarationType != null && !myLabeler.addMigrationRoot(variable, psiType, myStatement, TypeConversionUtil.isAssignable(declarationType, psiType), true) &&
                !TypeConversionUtil.isAssignable(left.getType(), psiType)) {
              PsiType initialType = left.getType();
              if (initialType instanceof PsiEllipsisType) {
                initialType = ((PsiEllipsisType) initialType).getComponentType();
              }
              myLabeler.convertExpression(value, psiType, initialType, isCovariantPosition);
            }
          } else {
            if (variable instanceof PsiLocalVariable) {
              PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
              if (decl != null && decl.getDeclaredElements().length == 1) {
                tryToRemoveLocalVariableAssignment((PsiLocalVariable) variable, value, psiType);
              }
              break;
            }
          }
        }
        break;

      case TypeInfection.BOTH_INFECTED:
        addTypeUsage(variable);
        break;

      default:
        LOG.error("Must not happen.");
    }
  }

  private void tryToRemoveLocalVariableAssignment(@Nonnull PsiLocalVariable variable, @Nonnull PsiExpression valueExpression, @Nonnull PsiType migrationType) {
    PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    PsiElement[] refs = DefUseUtil.getRefs(codeBlock, variable, valueExpression);
    if (refs.length == 0) {
      myLabeler.setConversionMapping(valueExpression, new TypeConversionDescriptorBase() {
        @Override
        public PsiExpression replace(PsiExpression expression, @Nonnull TypeEvaluator evaluator) throws IncorrectOperationException {
          PsiElement parent = expression.getParent();
          if (parent instanceof PsiLocalVariable) {
            PsiLocalVariable var = (PsiLocalVariable) parent;
            PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(var, PsiDeclarationStatement.class);
            if (decl == null) {
              return null;
            }
            Project project = var.getProject();
            PsiAssignmentExpression assignment = SplitDeclarationAction.invokeOnDeclarationStatement(decl, PsiManager.getInstance(project), project);
            PsiExpression rExpression = assignment.getRExpression();
            if (rExpression == null) {
              return null;
            }
            assignment.replace(rExpression);
            if (ReferencesSearch.search(var).forEach(new CommonProcessors.FindFirstProcessor<>())) {
              var.delete();
            }
          } else if (parent instanceof PsiAssignmentExpression) {
            PsiExpression rExpression = ((PsiAssignmentExpression) parent).getRExpression();
            return rExpression == null ? null : (PsiExpression) parent.replace(rExpression);
          }
          return null;
        }
      });
    } else {
      myLabeler.markFailedConversion(Pair.pair(null, migrationType), valueExpression);
    }
  }


  private static boolean canBeVariableType(@Nonnull PsiType type) {
    return !type.getDeepComponentType().equals(PsiType.VOID);
  }

  private static PsiType adjustMigrationTypeIfGenericArrayCreation(PsiType migrationType, PsiExpression expression) {
    if (expression instanceof PsiNewExpression) {
      if (migrationType instanceof PsiArrayType) {
        PsiType componentType = migrationType.getDeepComponentType();
        if (componentType instanceof PsiClassType) {
          PsiClassType rawType = ((PsiClassType) componentType).rawType();
          if (!rawType.equals(componentType)) {
            return com.intellij.java.impl.refactoring.typeCook.Util.createArrayType(rawType, migrationType.getArrayDimensions());
          }
        }
      }
    }
    return migrationType;
  }


  private void addTypeUsage(PsiElement typedElement) {
    if (typedElement instanceof PsiReferenceExpression) {
      myLabeler.setTypeUsage(((PsiReferenceExpression) typedElement).resolve(), myStatement);
    } else if (typedElement instanceof PsiMethodCallExpression) {
      myLabeler.setTypeUsage(((PsiMethodCallExpression) typedElement).resolveMethod(), myStatement);
    } else {
      myLabeler.setTypeUsage(typedElement, myStatement);
    }
  }


  private class TypeView {
    final PsiType myOriginType;
    final PsiType myType;
    final boolean myChanged;

    public TypeView(@Nonnull PsiExpression expr) {
      PsiType exprType = expr.getType();
      exprType = exprType instanceof PsiEllipsisType ? ((PsiEllipsisType) exprType).toArrayType() : exprType;
      myOriginType = GenericsUtil.getVariableTypeByExpressionType(exprType);
      PsiType type = myTypeEvaluator.evaluateType(expr);
      type = type instanceof PsiEllipsisType ? ((PsiEllipsisType) type).toArrayType() : type;
      myType = GenericsUtil.getVariableTypeByExpressionType(type);
      myChanged = !(myOriginType == null || myType == null) && !myType.equals(myOriginType);
    }

    public TypeView(PsiVariable var, PsiSubstitutor varSubstitutor, PsiSubstitutor evalSubstitutor) {
      myOriginType = varSubstitutor != null ? varSubstitutor.substitute(var.getType()) : var.getType();

      Map<PsiTypeParameter, PsiType> realMap = new HashMap<>();
      if (varSubstitutor != null) {
        realMap.putAll(varSubstitutor.getSubstitutionMap());
      }
      if (evalSubstitutor != null) {
        realMap.putAll(evalSubstitutor.getSubstitutionMap());
      }

      myType = PsiSubstitutorImpl.createSubstitutor(realMap).substitute(myTypeEvaluator.getType(var));
      myChanged = !(myOriginType == null || myType == null) && !myType.equals(myOriginType);
    }

    public PsiType getType() {
      return myType;
    }

    public boolean isChanged() {
      return myChanged;
    }

    public Pair<PsiType, PsiType> getTypePair() {
      return Pair.create(myOriginType, myType);
    }

    public boolean isVarArgs() {
      return myType instanceof PsiEllipsisType && myOriginType instanceof PsiEllipsisType;
    }
  }

  private static class TypeInfection {
    static final int NONE_INFECTED = 0;
    static final int LEFT_INFECTED = 1;
    static final int RIGHT_INFECTED = 2;
    static final int BOTH_INFECTED = 3;

    static int getInfection(TypeView left, TypeView right) {
      return (left.isChanged() ? 1 : 0) + (right.isChanged() ? 2 : 0);
    }
  }

  private static boolean isSetter(PsiAssignmentExpression expression) {
    PsiExpression lExpression = expression.getLExpression();
    if (lExpression instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression) lExpression).resolve();
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField) resolved;
        NavigatablePsiElement containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiLambdaExpression.class);
        if (containingMethod instanceof PsiMethod) {
          PsiMethod setter = PropertyUtil.findPropertySetter(field.getContainingClass(), field.getName(), field.hasModifierProperty(PsiModifier.STATIC), false);
          if (containingMethod.isEquivalentTo(setter)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isGetter(PsiExpression returnValue, PsiElement containingMethod) {
    if (returnValue instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression) returnValue).resolve();
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField) resolved;
        boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        PsiMethod[] getters = GetterSetterPrototypeProvider.findGetters(field.getContainingClass(), field.getName(), isStatic);
        if (getters != null) {
          for (PsiMethod getter : getters) {
            if (containingMethod.isEquivalentTo(getter)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static PsiSubstitutor composeIfNotAssignable(PsiSubstitutor actual, PsiSubstitutor required) {
    if (actual == PsiSubstitutor.EMPTY) {
      return required;
    }
    if (required == PsiSubstitutor.EMPTY) {
      return actual;
    }
    PsiSubstitutor result = PsiSubstitutorImpl.createSubstitutor(actual.getSubstitutionMap());
    for (Map.Entry<PsiTypeParameter, PsiType> e : required.getSubstitutionMap().entrySet()) {
      PsiTypeParameter typeParameter = e.getKey();
      PsiType requiredType = e.getValue();
      PsiType actualType = result.getSubstitutionMap().get(typeParameter);
      if (requiredType != null && (actualType == null || !TypeConversionUtil.isAssignable(actualType, requiredType))) {
        result = result.put(typeParameter, requiredType);
      }
    }
    return result;
  }

  private static boolean isVarargAssignable(TypeView left, TypeView right) {
    Pair<PsiType, PsiType> leftPair = left.getTypePair();
    Pair<PsiType, PsiType> rightPair = right.getTypePair();

    PsiType leftOrigin = leftPair.getFirst();
    PsiType rightOrigin = rightPair.getFirst();

    boolean isDirectlyAssignable = TypeConversionUtil.isAssignable(leftOrigin, rightOrigin);

    return TypeConversionUtil.isAssignable(isDirectlyAssignable ? leftPair.getSecond() : ((PsiEllipsisType) leftPair.getSecond()).getComponentType(), rightPair.getSecond());
  }
}
