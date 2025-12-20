/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.impl.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.util.lang.Pair;

import java.util.*;

/**
 * @author anna
 */
public class ClassTypeArgumentMigrationProcessor {
  private static final Logger LOG = Logger.getInstance(ClassTypeArgumentMigrationProcessor.class);

  private final TypeMigrationLabeler myLabeler;

  public ClassTypeArgumentMigrationProcessor(TypeMigrationLabeler labeler) {
    myLabeler = labeler;
  }

  public void migrateClassTypeParameter(PsiReferenceParameterList referenceParameterList, PsiClassType migrationType) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceParameterList, PsiClass.class);
    LOG.assertTrue(psiClass != null);

    PsiClass superClass = psiClass.getSuperClass();
    LOG.assertTrue(superClass != null);

    myLabeler.getTypeEvaluator().setType(new TypeMigrationUsageInfo(superClass), migrationType);


    Map<PsiElement, Pair<PsiReference[], PsiType>> roots = new HashMap<>();

    markTypeParameterUsages(psiClass, migrationType, referenceParameterList, roots);

    Set<PsiElement> processed = new HashSet<>();
    for (Map.Entry<PsiElement, Pair<PsiReference[], PsiType>> entry : roots.entrySet()) {
      PsiElement member = entry.getKey();
      PsiType type = entry.getValue().second;

      if (member instanceof PsiParameter && ((PsiParameter) member).getDeclarationScope() instanceof PsiMethod) {
        myLabeler.migrateMethodCallExpressions(type, (PsiParameter) member, psiClass);
      }


      PsiReference[] references = entry.getValue().first;
      for (PsiReference usage : references) {
        myLabeler.migrateRootUsageExpression(usage, processed);
      }
    }
  }

  private void markTypeParameterUsages(final PsiClass psiClass,
                                       PsiClassType migrationType,
                                       PsiReferenceParameterList referenceParameterList,
                                       final Map<PsiElement, Pair<PsiReference[], PsiType>> roots) {

    final PsiSubstitutor[] fullHierarchySubstitutor = {migrationType.resolveGenerics().getSubstitutor()};
    RefactoringHierarchyUtil.processSuperTypes(migrationType, new RefactoringHierarchyUtil.SuperTypeVisitor() {
      @Override
      public void visitType(PsiType aType) {
        fullHierarchySubstitutor[0] = fullHierarchySubstitutor[0].putAll(((PsiClassType) aType).resolveGenerics().getSubstitutor());
      }

      @Override
      public void visitClass(PsiClass aClass) {
        //do nothing
      }
    });

    PsiClass resolvedClass = (PsiClass) ((PsiJavaCodeReferenceElement) referenceParameterList.getParent()).resolve();
    LOG.assertTrue(resolvedClass != null);
    Set<PsiClass> superClasses = new HashSet<>();
    superClasses.add(resolvedClass);
    InheritanceUtil.getSuperClasses(resolvedClass, superClasses, true);
    for (PsiClass superSuperClass : superClasses) {
      final Set<PsiTypeParameter> typeParameters = Set.copyOf(PsiUtil.typeParametersIterable(superSuperClass));
      superSuperClass.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitMethod(PsiMethod method) {
          super.visitMethod(method);
          processMemberType(method, typeParameters, psiClass, fullHierarchySubstitutor[0], roots);
          for (PsiParameter parameter : method.getParameterList().getParameters()) {
            processMemberType(parameter, typeParameters, psiClass, fullHierarchySubstitutor[0], roots);
          }
        }

        @Override
        public void visitField(PsiField field) {
          super.visitField(field);
          processMemberType(field, typeParameters, psiClass, fullHierarchySubstitutor[0], roots);
        }
      });
    }

  }

  private void processMemberType(PsiElement element,
                                 Set<PsiTypeParameter> typeParameters,
                                 PsiClass psiClass,
                                 PsiSubstitutor substitutor,
                                 Map<PsiElement, Pair<PsiReference[], PsiType>> roots) {
    PsiType elementType = TypeMigrationLabeler.getElementType(element);
    if (elementType != null && PsiPolyExpressionUtil.mentionsTypeParameters(elementType, typeParameters)) {
      PsiType memberType = substitutor.substitute(elementType);

      prepareMethodsChangeSignature(psiClass, element, memberType);

      List<PsiReference> refs = TypeMigrationLabeler.filterReferences(psiClass, ReferencesSearch.search(element, psiClass.getUseScope()));

      roots.put(element, Pair.create(myLabeler.markRootUsages(element, memberType, refs.toArray(PsiReference.EMPTY_ARRAY)), memberType));
    }
  }

  /**
   * signature should be changed for methods with type parameters
   */
  private void prepareMethodsChangeSignature(PsiClass currentClass, PsiElement memberToChangeSignature, PsiType memberType) {
    if (memberToChangeSignature instanceof PsiMethod) {
      PsiMethod method = MethodSignatureUtil.findMethodBySuperMethod(currentClass, (PsiMethod) memberToChangeSignature, true);
      if (method != null && method.getContainingClass() == currentClass) {
        myLabeler.addRoot(new TypeMigrationUsageInfo(method), memberType, method, false);
      }
    } else if (memberToChangeSignature instanceof PsiParameter && ((PsiParameter) memberToChangeSignature).getDeclarationScope() instanceof PsiMethod) {
      PsiMethod superMethod = (PsiMethod) ((PsiParameter) memberToChangeSignature).getDeclarationScope();
      int parameterIndex = superMethod.getParameterList().getParameterIndex((PsiParameter) memberToChangeSignature);
      PsiMethod method = MethodSignatureUtil.findMethodBySuperMethod(currentClass, superMethod, true);
      if (method != null && method.getContainingClass() == currentClass) {
        PsiParameter parameter = method.getParameterList().getParameters()[parameterIndex];
        if (!parameter.getType().equals(memberType)) {
          myLabeler.addRoot(new TypeMigrationUsageInfo(parameter), memberType, parameter, false);
        }
      }
    }
  }
}
