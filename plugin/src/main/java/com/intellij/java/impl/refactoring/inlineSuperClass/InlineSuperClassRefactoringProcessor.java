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

/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.java.impl.refactoring.inlineSuperClass;

import com.intellij.java.impl.refactoring.inlineSuperClass.usageInfo.*;
import com.intellij.java.impl.refactoring.memberPushDown.PushDownConflicts;
import com.intellij.java.impl.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.impl.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.impl.idea.refactoring.util.DocCommentPolicy;
import consulo.ide.impl.idea.util.ArrayUtilRt;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InlineSuperClassRefactoringProcessor extends FixableUsagesRefactoringProcessor {
  public static final Logger LOG = Logger.getInstance(InlineSuperClassRefactoringProcessor.class);

  private final PsiClass myCurrentInheritor;
  private final PsiClass mySuperClass;
  private final int myPolicy;
  private final PsiClass[] myTargetClasses;
  private final MemberInfo[] myMemberInfos;

  public InlineSuperClassRefactoringProcessor(
    Project project,
    PsiClass currentInheritor,
    PsiClass superClass,
    int policy,
    final PsiClass... targetClasses
  ) {
    super(project);
    myCurrentInheritor = currentInheritor;
    mySuperClass = superClass;
    myPolicy = policy;
    myTargetClasses = currentInheritor != null ? new PsiClass[]{currentInheritor} : targetClasses;
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(mySuperClass, new MemberInfo.Filter<PsiMember>() {
      public boolean includeMember(PsiMember element) {
        return !(element instanceof PsiClass) || PsiTreeUtil.isAncestor(mySuperClass, element, true);
      }
    });
    List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(mySuperClass);
    for (MemberInfo member : members) {
      member.setChecked(true);
    }
    myMemberInfos = members.toArray(new MemberInfo[members.size()]);
  }

  @Nonnull
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new InlineSuperClassUsageViewDescriptor(mySuperClass);
  }

  @RequiredReadAction
  protected void findUsages(@Nonnull final List<FixableUsageInfo> usages) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiElementFactory elementFactory = facade.getElementFactory();
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();

    ReferencesSearch.search(mySuperClass).forEach(reference -> {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiJavaCodeReferenceElement) {
        if (myCurrentInheritor != null) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiReferenceList) {
            final PsiElement pparent = parent.getParent();
            if (pparent instanceof PsiClass) {
              final PsiClass inheritor = (PsiClass) pparent;
              if (parent.equals(inheritor.getExtendsList()) || parent.equals(inheritor.getImplementsList())) {
                if (myCurrentInheritor.equals(inheritor)) {
                  usages.add(new ReplaceExtendsListUsageInfo((PsiJavaCodeReferenceElement) element, mySuperClass, inheritor));
                }
              }
            }
          }
          return true;
        }
        final PsiImportStaticStatement staticImportStatement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
        if (staticImportStatement != null) {
          usages.add(new ReplaceStaticImportUsageInfo(staticImportStatement, myTargetClasses));
        } else {
          final PsiImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
          if (importStatement != null) {
            usages.add(new RemoveImportUsageInfo(importStatement));
          } else {
            final PsiElement parent = element.getParent();
            if (parent instanceof PsiReferenceList) {
              final PsiElement pparent = parent.getParent();
              if (pparent instanceof PsiClass) {
                final PsiClass inheritor = (PsiClass) pparent;
                if (parent.equals(inheritor.getExtendsList()) || parent.equals(inheritor.getImplementsList())) {
                  usages.add(new ReplaceExtendsListUsageInfo((PsiJavaCodeReferenceElement) element, mySuperClass, inheritor));
                }
              }
            } else {
              final PsiClass targetClass = myTargetClasses[0];
              final PsiClassType targetClassType = elementFactory
                  .createType(targetClass, TypeConversionUtil.getSuperClassSubstitutor(mySuperClass, targetClass, PsiSubstitutor.EMPTY));

              if (parent instanceof PsiTypeElement) {
                final PsiType superClassType = ((PsiTypeElement) parent).getType();
                PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper, targetClass);
                usages.add(new ReplaceWithSubtypeUsageInfo(((PsiTypeElement) parent), elementFactory.createType(targetClass, subst), myTargetClasses));
              } else if (parent instanceof PsiNewExpression) {
                final PsiClassType newType = elementFactory.createType(targetClass,
                    getSuperClassSubstitutor(((PsiNewExpression) parent).getType(),
                        targetClassType, resolveHelper,
                        targetClass));
                usages.add(new ReplaceConstructorUsageInfo(((PsiNewExpression) parent), newType, myTargetClasses));
              } else if (parent instanceof PsiJavaCodeReferenceElement) {
                usages.add(new ReplaceReferenceUsageInfo(((PsiJavaCodeReferenceElement) parent).getQualifier(), myTargetClasses));
              }
            }
          }
        }
      }
      return true;
    });
    for (PsiClass targetClass : myTargetClasses) {
      for (MemberInfo memberInfo : myMemberInfos) {
        final PsiMember member = memberInfo.getMember();
        for (PsiReference reference : ReferencesSearch.search(member, member.getUseScope(), true)) {
          final PsiElement element = reference.getElement();
          if (element instanceof PsiReferenceExpression &&
              ((PsiReferenceExpression) element).getQualifierExpression() instanceof PsiSuperExpression &&
              PsiTreeUtil.isAncestor(targetClass, element, false)) {
            usages.add(new RemoveQualifierUsageInfo((PsiReferenceExpression) element));
          }
        }
      }

      final PsiMethod[] superConstructors = mySuperClass.getConstructors();
      for (PsiMethod constructor : targetClass.getConstructors()) {
        final PsiCodeBlock constrBody = constructor.getBody();
        LOG.assertTrue(constrBody != null);
        final PsiStatement[] statements = constrBody.getStatements();
        if (statements.length > 0) {
          final PsiStatement firstConstrStatement = statements[0];
          if (firstConstrStatement instanceof PsiExpressionStatement) {
            final PsiExpression expression = ((PsiExpressionStatement) firstConstrStatement).getExpression();
            if (expression instanceof PsiMethodCallExpression) {
              final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression) expression).getMethodExpression();
              if (methodExpression.getText().equals(PsiKeyword.SUPER)) {
                final PsiMethod superConstructor = ((PsiMethodCallExpression) expression).resolveMethod();
                if (superConstructor != null && superConstructor.getBody() != null) {
                  usages.add(new InlineSuperCallUsageInfo((PsiMethodCallExpression) expression));
                  continue;
                }
              }
            }
          }
        }

        //insert implicit call to super
        for (PsiMethod superConstructor : superConstructors) {
          if (superConstructor.getParameterList().getParametersCount() == 0) {
            final PsiExpression expression = JavaPsiFacade.getElementFactory(myProject).createExpressionFromText("super()", constructor);
            usages.add(new InlineSuperCallUsageInfo((PsiMethodCallExpression) expression, constrBody));
          }
        }
      }

      if (targetClass.getConstructors().length == 0) {
        //copy default constructor
        for (PsiMethod superConstructor : superConstructors) {
          if (superConstructor.getParameterList().getParametersCount() == 0) {
            usages.add(new CopyDefaultConstructorUsageInfo(targetClass, superConstructor));
            break;
          }
        }
      }
    }
  }

  @Override
  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final PushDownConflicts pushDownConflicts = new PushDownConflicts(mySuperClass, myMemberInfos);
    for (PsiClass targetClass : myTargetClasses) {
      for (MemberInfo info : myMemberInfos) {
        final PsiMember member = info.getMember();
        pushDownConflicts.checkMemberPlacementInTargetClassConflict(targetClass, member);
      }
      //todo check accessibility conflicts
    }
    final MultiMap<PsiElement, String> conflictsMap = pushDownConflicts.getConflicts();
    for (PsiElement element : conflictsMap.keySet()) {
      conflicts.put(element, conflictsMap.get(element));
    }
    if (myCurrentInheritor != null) {
      ReferencesSearch.search(myCurrentInheritor).forEach(reference -> {
        final PsiElement element = reference.getElement();
        if (element != null) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiNewExpression) {
            final PsiClass aClass = PsiUtil.resolveClassInType(getPlaceExpectedType(parent));
            if (aClass == mySuperClass) {
              conflicts.putValue(parent, "Instance of target type is passed to a place where super class is expected.");
              return false;
            }
          }
        }
        return true;
      });
    }
    checkConflicts(refUsages, conflicts);
    return showConflicts(conflicts, refUsages.get());
  }

  @Nullable
  private static PsiType getPlaceExpectedType(PsiElement parent) {
    PsiType type = PsiTypesUtil.getExpectedTypeByParent(parent);
    if (type == null) {
      final PsiElement arg = PsiUtil.skipParenthesizedExprUp(parent);
      final PsiElement gParent = arg.getParent();
      if (gParent instanceof PsiExpressionList) {
        int i = ArrayUtilRt.find(((PsiExpressionList) gParent).getExpressions(), arg);
        final PsiElement pParent = gParent.getParent();
        if (pParent instanceof PsiCallExpression) {
          final PsiMethod method = ((PsiCallExpression) pParent).resolveMethod();
          if (method != null) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            if (i >= parameters.length) {
              if (method.isVarArgs()) {
                return ((PsiEllipsisType) parameters[parameters.length - 1].getType()).getComponentType();
              }
            } else {
              return parameters[i].getType();
            }
          }
        }
      }
    }
    return type;
  }

  protected void performRefactoring(final UsageInfo[] usages) {
    new PushDownProcessor(mySuperClass.getProject(), myMemberInfos, mySuperClass, new DocCommentPolicy(myPolicy)) {
      //push down conflicts are already collected
      @Override
      protected boolean showConflicts(@Nonnull MultiMap<PsiElement, String> conflicts, UsageInfo[] usages) {
        return true;
      }

      @Override
      @RequiredUIAccess
      protected void performRefactoring(@Nonnull UsageInfo[] pushDownUsages) {
        if (myCurrentInheritor != null) {
          encodeRefs();
          pushDownToClass(myCurrentInheritor);
        } else {
          super.performRefactoring(pushDownUsages);
        }
        CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);
        for (UsageInfo usageInfo : usages) {
          if (!(usageInfo instanceof ReplaceExtendsListUsageInfo || usageInfo instanceof RemoveImportUsageInfo)) {
            try {
              ((FixableUsageInfo) usageInfo).fixUsage();
            } catch (IncorrectOperationException e) {
              LOG.info(e);
            }
          }
        }
        replaceInnerTypeUsages();

        //postpone broken hierarchy
        for (UsageInfo usage : usages) {
          if (usage instanceof ReplaceExtendsListUsageInfo || usage instanceof RemoveImportUsageInfo) {
            ((FixableUsageInfo) usage).fixUsage();
          }
        }
        if (myCurrentInheritor == null) {
          try {
            mySuperClass.delete();
          } catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }.run();
  }

  private void replaceInnerTypeUsages() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiElementFactory elementFactory = facade.getElementFactory();
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final Map<UsageInfo, PsiElement> replacementMap = new HashMap<>();
    for (final PsiClass targetClass : myTargetClasses) {
      final PsiSubstitutor superClassSubstitutor =
          TypeConversionUtil.getSuperClassSubstitutor(mySuperClass, targetClass, PsiSubstitutor.EMPTY);
      final PsiClassType targetClassType = elementFactory.createType(targetClass, superClassSubstitutor);
      targetClass.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitTypeElement(@Nonnull final PsiTypeElement typeElement) {
          super.visitTypeElement(typeElement);
          final PsiType superClassType = typeElement.getType();
          if (PsiUtil.resolveClassInType(superClassType) == mySuperClass) {
            PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper, targetClass);
            replacementMap.put(new UsageInfo(typeElement), elementFactory.createTypeElement(elementFactory.createType(targetClass, subst)));
          }
        }

        @Override
        @RequiredReadAction
        public void visitNewExpression(@Nonnull final PsiNewExpression expression) {
          super.visitNewExpression(expression);
          final PsiType superClassType = expression.getType();
          if (PsiUtil.resolveClassInType(superClassType) == mySuperClass) {
            PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper, targetClass);
            try {
              replacementMap.put(new UsageInfo(expression), elementFactory.createExpressionFromText("new " + elementFactory.createType(
                  targetClass, subst).getCanonicalText() + expression.getArgumentList().getText(), expression));
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      });
    }
    try {
      for (Map.Entry<UsageInfo, PsiElement> elementEntry : replacementMap.entrySet()) {
        final PsiElement element = elementEntry.getKey().getElement();
        if (element != null) {
          element.replace(elementEntry.getValue());
        }
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @RequiredReadAction
  private static PsiSubstitutor getSuperClassSubstitutor(
    final PsiType superClassType,
    final PsiClassType targetClassType,
    final PsiResolveHelper resolveHelper,
    PsiClass targetClass
  ) {
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(targetClass)) {
      subst = subst.put(
        typeParameter,
        resolveHelper.getSubstitutionForTypeParameter(
          typeParameter,
          targetClassType,
          superClassType,
          false,
          PsiUtil.getLanguageLevel(targetClass)
        )
      );
    }
    return subst;
  }

  protected String getCommandName() {
    return InlineSuperClassRefactoringHandler.REFACTORING_NAME;
  }
}
