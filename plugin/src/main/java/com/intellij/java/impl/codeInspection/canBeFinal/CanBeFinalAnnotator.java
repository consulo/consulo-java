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
package com.intellij.java.impl.codeInspection.canBeFinal;

import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefField;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.analysis.impl.codeInspection.reference.RefClassImpl;
import com.intellij.java.analysis.impl.codeInspection.reference.RefMethodImpl;
import com.intellij.java.impl.codeInspection.reference.RefFieldImpl;
import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.psi.*;
import consulo.language.editor.impl.inspection.reference.RefElementImpl;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefGraphAnnotatorEx;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import java.util.*;

/**
 * User: anna
 * Date: 27-Dec-2005
 */
class CanBeFinalAnnotator extends RefGraphAnnotatorEx {
  private final RefManager myManager;
  public static long CAN_BE_FINAL_MASK;

  public CanBeFinalAnnotator(@Nonnull RefManager manager) {
    myManager = manager;
  }

  @Override
  public void initialize(RefManager refManager) {
    CAN_BE_FINAL_MASK = refManager.getLastUsedMask();
  }

  @Override
  public void onInitialize(RefElement refElement) {
    ((RefElementImpl) refElement).setFlag(true, CAN_BE_FINAL_MASK);
    if (refElement instanceof RefClass) {
      final RefClass refClass = (RefClass) refElement;
      final PsiClass psiClass = refClass.getElement();
      if (refClass.isEntry()) {
        ((RefClassImpl) refClass).setFlag(false, CAN_BE_FINAL_MASK);
        return;
      }
      if (refClass.isAbstract() || refClass.isAnonymous() || refClass.isInterface()) {
        ((RefClassImpl) refClass).setFlag(false, CAN_BE_FINAL_MASK);
        return;
      }
      if (!refClass.isSelfInheritor(psiClass)) {
        for (PsiClass psiSuperClass : psiClass.getSupers()) {
          if (myManager.belongsToScope(psiSuperClass)) {
            RefClass refSuperClass = (RefClass) myManager.getReference(psiSuperClass);
            if (refSuperClass != null) {
              ((RefClassImpl) refSuperClass).setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }
      }
    } else if (refElement instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod) refElement;
      final PsiElement element = refMethod.getElement();
      if (element instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod) element;
        if (refMethod.isConstructor() || refMethod.isAbstract() || refMethod.isStatic() ||
            PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) || refMethod.getOwnerClass().isAnonymous() ||
            refMethod.getOwnerClass().isInterface()) {
          ((RefMethodImpl) refMethod).setFlag(false, CAN_BE_FINAL_MASK);
        }
        if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) && refMethod.getOwner() != null &&
            !(refMethod.getOwnerClass().getOwner() instanceof RefElement)) {
          ((RefMethodImpl) refMethod).setFlag(false, CAN_BE_FINAL_MASK);
        }
        for (PsiMethod psiSuperMethod : psiMethod.findSuperMethods()) {
          if (myManager.belongsToScope(psiSuperMethod)) {
            RefMethod refSuperMethod = (RefMethod) myManager.getReference(psiSuperMethod);
            if (refSuperMethod != null) {
              ((RefMethodImpl) refSuperMethod).setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }
      }
    }
  }


  @Override
  public void onMarkReferenced(RefElement refWhat,
                               RefElement refFrom,
                               boolean referencedFromClassInitializer,
                               boolean forReading,
                               boolean forWriting) {
    if (!(refWhat instanceof RefField)) return;
    if (!(refFrom instanceof RefMethod) ||
        !((RefMethod) refFrom).isConstructor() ||
        ((PsiField) refWhat.getElement()).hasInitializer() ||
        ((RefMethod) refFrom).getOwnerClass() != ((RefField) refWhat).getOwnerClass() ||
        ((RefField) refWhat).isStatic()) {
      if (!referencedFromClassInitializer && forWriting) {
        ((RefFieldImpl) refWhat).setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
  }

  @Override
  public void onReferencesBuild(RefElement refElement) {
    if (refElement instanceof RefClass) {
      final PsiClass psiClass = (PsiClass) refElement.getElement();
      if (psiClass != null) {

        if (refElement.isEntry()) {
          ((RefClassImpl) refElement).setFlag(false, CAN_BE_FINAL_MASK);
        }

        PsiMethod[] psiMethods = psiClass.getMethods();
        PsiField[] psiFields = psiClass.getFields();

        Set<PsiVariable> allFields = new HashSet<PsiVariable>();
        ContainerUtil.addAll(allFields, psiFields);
        List<PsiVariable> instanceInitializerInitializedFields = new ArrayList<PsiVariable>();
        boolean hasInitializers = false;
        for (PsiClassInitializer initializer : psiClass.getInitializers()) {
          PsiCodeBlock body = initializer.getBody();
          hasInitializers = true;
          ControlFlow flow;
          try {
            flow = ControlFlowFactory.getInstance(body.getProject())
                .getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
          } catch (AnalysisCanceledException e) {
            flow = ControlFlow.EMPTY;
          }
          Collection<PsiVariable> writtenVariables = new ArrayList<PsiVariable>();
          ControlFlowUtil.getWrittenVariables(flow, 0, flow.getSize(), false, writtenVariables);
          for (PsiVariable psiVariable : writtenVariables) {
            if (allFields.contains(psiVariable)) {
              if (instanceInitializerInitializedFields.contains(psiVariable)) {
                allFields.remove(psiVariable);
                instanceInitializerInitializedFields.remove(psiVariable);
              } else {
                instanceInitializerInitializedFields.add(psiVariable);
              }
            }
          }
          for (PsiVariable psiVariable : writtenVariables) {
            if (!instanceInitializerInitializedFields.contains(psiVariable)) {
              allFields.remove(psiVariable);
            }
          }
        }

        for (PsiMethod psiMethod : psiMethods) {
          if (psiMethod.isConstructor()) {
            PsiCodeBlock body = psiMethod.getBody();
            if (body != null) {
              hasInitializers = true;
              ControlFlow flow;
              try {
                flow = ControlFlowFactory.getInstance(body.getProject())
                    .getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
              } catch (AnalysisCanceledException e) {
                flow = ControlFlow.EMPTY;
              }

              Collection<PsiVariable> writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, flow.getSize(), false);
              for (PsiVariable psiVariable : writtenVariables) {
                if (instanceInitializerInitializedFields.contains(psiVariable)) {
                  allFields.remove(psiVariable);
                  instanceInitializerInitializedFields.remove(psiVariable);
                }
              }
              List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(psiMethod);
              if (redirectedConstructors.isEmpty()) {
                List<PsiVariable> ssaVariables = ControlFlowUtil.getSSAVariables(flow);
                ArrayList<PsiVariable> good = new ArrayList<PsiVariable>(ssaVariables);
                good.addAll(instanceInitializerInitializedFields);
                allFields.retainAll(good);
              } else {
                allFields.removeAll(writtenVariables);
              }
            }
          }
        }

        for (PsiField psiField : psiFields) {
          if ((!hasInitializers || !allFields.contains(psiField)) && psiField.getInitializer() == null) {
            final RefFieldImpl refField = (RefFieldImpl) myManager.getReference(psiField);
            if (refField != null) {
              refField.setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }

      }
    } else if (refElement instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod) refElement;
      if (refMethod.isEntry()) {
        ((RefMethodImpl) refMethod).setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
  }
}
