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
package com.intellij.java.impl.util.xml.impl;

import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.java.impl.util.xml.ExtendClass;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ProcessingContext;
import consulo.project.Project;
import consulo.util.collection.SmartList;
import consulo.util.lang.reflect.ReflectionUtil;
import consulo.xml.util.xml.DomBundle;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomUtil;
import consulo.xml.util.xml.GenericDomValue;
import consulo.xml.util.xml.highlighting.DomCustomAnnotationChecker;
import consulo.xml.util.xml.highlighting.DomElementAnnotationHolder;
import consulo.xml.util.xml.highlighting.DomElementProblemDescriptor;
import consulo.xml.util.xml.highlighting.DomHighlightingHelper;
import consulo.xml.util.xml.impl.GenericValueReferenceProvider;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
@ExtensionImpl
public class ExtendsClassChecker extends DomCustomAnnotationChecker<ExtendClass> {
  private static final GenericValueReferenceProvider ourProvider = new GenericValueReferenceProvider();

  @Nonnull
  public Class<ExtendClass> getAnnotationClass() {
    return ExtendClass.class;
  }

  public List<DomElementProblemDescriptor> checkForProblems(@Nonnull ExtendClass extend, @Nonnull DomElement _element, @Nonnull DomElementAnnotationHolder holder,
                                                            @Nonnull DomHighlightingHelper helper) {
    if (!(_element instanceof GenericDomValue)) return Collections.emptyList();
    GenericDomValue element = (GenericDomValue) _element;

    if (!isPsiClassType(element)) return Collections.emptyList();

    Object valueObject = element.getValue();
    PsiClass psiClass = null;

    if (valueObject instanceof PsiClass) {
      psiClass = (PsiClass) valueObject;
    } else if (valueObject instanceof PsiClassType) {
      psiClass = ((PsiClassType) valueObject).resolve();
    }

    if (psiClass != null) {
      return checkExtendClass(element, psiClass, extend.value(),
          extend.instantiatable(), extend.canBeDecorator(), extend.allowInterface(),
          extend.allowNonPublic(), extend.allowAbstract(), extend.allowEnum(), holder);
    }
    return Collections.emptyList();
  }

  @Nonnull
  public static List<DomElementProblemDescriptor> checkExtendClass(GenericDomValue element, PsiClass value, String name,
                                                                   boolean instantiatable, boolean canBeDecorator, boolean allowInterface,
                                                                   boolean allowNonPublic,
                                                                   boolean allowAbstract,
                                                                   boolean allowEnum,
                                                                   DomElementAnnotationHolder holder) {
    Project project = element.getManager().getProject();
    PsiClass extendClass = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
    SmartList<DomElementProblemDescriptor> list = new SmartList<DomElementProblemDescriptor>();
    if (extendClass != null) {
      if (!name.equals(value.getQualifiedName()) && !value.isInheritor(extendClass, true)) {
        String message = DomBundle.message("class.is.not.a.subclass", value.getQualifiedName(), extendClass.getQualifiedName());
        list.add(holder.createProblem(element, message));
      }
    }

    if (instantiatable) {
      if (value.hasModifierProperty(PsiModifier.ABSTRACT)) {
        list.add(holder.createProblem(element, DomBundle.message("class.is.not.concrete", value.getQualifiedName())));
      } else if (!allowNonPublic && !value.hasModifierProperty(PsiModifier.PUBLIC)) {
        list.add(holder.createProblem(element, DomBundle.message("class.is.not.public", value.getQualifiedName())));
      } else if (!PsiUtil.hasDefaultConstructor(value, true)) {
        if (canBeDecorator) {
          boolean hasConstructor = false;

          for (PsiMethod method : value.getConstructors()) {
            PsiParameterList psiParameterList = method.getParameterList();
            if (psiParameterList.getParametersCount() != 1) continue;
            PsiTypeElement typeElement = psiParameterList.getParameters()[0].getTypeElement();
            if (typeElement != null) {
              PsiType psiType = typeElement.getType();
              if (psiType instanceof PsiClassType) {
                PsiClass psiClass = ((PsiClassType) psiType).resolve();
                if (psiClass != null && InheritanceUtil.isInheritorOrSelf(psiClass, extendClass, true)) {
                  hasConstructor = true;
                  break;
                }
              }
            }
          }
          if (!hasConstructor) {
            list.add(holder.createProblem(element, DomBundle.message("class.decorator.or.has.default.constructor", value.getQualifiedName())));
          }
        } else {
          list.add(holder.createProblem(element, DomBundle.message("class.has.no.default.constructor", value.getQualifiedName())));
        }
      }
    }
    if (!allowInterface && value.isInterface()) {
      list.add(holder.createProblem(element, DomBundle.message("interface.not.allowed", value.getQualifiedName())));
    }
    if (!allowEnum && value.isEnum()) {
      list.add(holder.createProblem(element, DomBundle.message("enum.not.allowed", value.getQualifiedName())));
    }
    if (!allowAbstract && value.hasModifierProperty(PsiModifier.ABSTRACT) && !value.isInterface()) {
      list.add(holder.createProblem(element, DomBundle.message("abstract.class.not.allowed", value.getQualifiedName())));
    }
    return list;
  }

  public static List<DomElementProblemDescriptor> checkExtendsClassInReferences(GenericDomValue element, DomElementAnnotationHolder holder) {
    if (!isPsiClassType(element)) {
      return Collections.emptyList();
    }

    Object valueObject = element.getValue();
    if (!(valueObject instanceof PsiClass)) return Collections.emptyList();

    PsiReference[] references = ourProvider.getReferencesByElement(DomUtil.getValueElement(element), new ProcessingContext());
    for (PsiReference reference : references) {
      if (reference instanceof JavaClassReference) {
        PsiReferenceProvider psiReferenceProvider = ((JavaClassReference) reference).getProvider();
        String[] value = psiReferenceProvider instanceof JavaClassReferenceProvider ? JavaClassReferenceProvider.EXTEND_CLASS_NAMES
            .getValue(((JavaClassReferenceProvider) psiReferenceProvider).getOptions()) : null;
        if (value != null && value.length != 0) {
          for (String className : value) {
            List<DomElementProblemDescriptor> problemDescriptors =
                checkExtendClass(element, ((PsiClass) valueObject), className, false, false, true, false, true, true, holder);
            if (!problemDescriptors.isEmpty()) {
              return problemDescriptors;
            }
          }
        }
      }
    }
    return Collections.emptyList();
  }

  private static boolean isPsiClassType(GenericDomValue element) {
    Class genericValueParameter = DomUtil.getGenericValueParameter(element.getDomElementType());
    if (genericValueParameter != null && (ReflectionUtil.isAssignable(genericValueParameter, PsiClass.class) || ReflectionUtil.isAssignable(genericValueParameter, PsiType.class))) {
      return true;
    }
    return false;
  }
}
