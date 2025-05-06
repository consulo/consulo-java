/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.smartPointers;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiManager;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author max
 */
@Singleton
@ServiceImpl
public class SmartTypePointerManagerImpl extends SmartTypePointerManager {
    private static final SmartTypePointer NULL_POINTER = () -> null;

    private final SmartPointerManager myPsiPointerManager;
    private final Project myProject;

    @Inject
    public SmartTypePointerManagerImpl(SmartPointerManager psiPointerManager, Project project) {
        myPsiPointerManager = psiPointerManager;
        myProject = project;
    }

    @Override
    @Nonnull
    public SmartTypePointer createSmartTypePointer(@Nonnull PsiType type) {
        SmartTypePointer pointer = type.accept(new SmartTypeCreatingVisitor());
        return pointer != null ? pointer : NULL_POINTER;
    }

    private static class SimpleTypePointer implements SmartTypePointer {
        private final PsiType myType;

        private SimpleTypePointer(@Nonnull PsiType type) {
            myType = type;
        }

        @Override
        public PsiType getType() {
            return myType;
        }
    }

    private static class ArrayTypePointer extends TypePointerBase<PsiArrayType> {
        private final SmartTypePointer myComponentTypePointer;

        public ArrayTypePointer(@Nonnull PsiArrayType type, @Nonnull SmartTypePointer componentTypePointer) {
            super(type);
            myComponentTypePointer = componentTypePointer;
        }

        @Nullable
        @Override
        protected PsiArrayType calcType() {
            PsiType type = myComponentTypePointer.getType();
            return type == null ? null : new PsiArrayType(type);
        }
    }

    private static class WildcardTypePointer extends TypePointerBase<PsiWildcardType> {
        private final PsiManager myManager;
        private final SmartTypePointer myBoundPointer;
        private final boolean myIsExtending;

        public WildcardTypePointer(@Nonnull PsiWildcardType type, @Nullable SmartTypePointer boundPointer) {
            super(type);
            myManager = type.getManager();
            myBoundPointer = boundPointer;
            myIsExtending = type.isExtends();
        }

        @Override
        protected PsiWildcardType calcType() {
            if (myBoundPointer == null) {
                return PsiWildcardType.createUnbounded(myManager);
            }
            else {
                PsiType type = myBoundPointer.getType();
                assert type != null : myBoundPointer;
                if (myIsExtending) {
                    return PsiWildcardType.createExtends(myManager, type);
                }
                return PsiWildcardType.createSuper(myManager, type);
            }
        }
    }

    private static class ClassTypePointer extends TypePointerBase<PsiClassType> {
        private final SmartPsiElementPointer<?> myClass;
        private final LanguageLevel myLevel;
        private final Map<SmartPsiElementPointer<PsiTypeParameter>, SmartTypePointer> myMap;
        private final SmartPsiElementPointer<?>[] myAnnotations;

        ClassTypePointer(
            @Nonnull PsiClassType type,
            @Nonnull SmartPsiElementPointer<?> aClass,
            @Nonnull LanguageLevel languageLevel,
            @Nonnull Map<SmartPsiElementPointer<PsiTypeParameter>, SmartTypePointer> map,
            @Nonnull SmartPsiElementPointer<?>[] annotations
        ) {
            super(type);
            myClass = aClass;
            myLevel = languageLevel;
            myMap = map;
            myAnnotations = annotations;
        }

        @Override
        @RequiredReadAction
        protected PsiClassType calcType() {
            if (!(myClass.getElement() instanceof PsiClass classElement)) {
                return null;
            }
            Map<PsiTypeParameter, PsiType> resurrected = new HashMap<>();
            Set<Map.Entry<SmartPsiElementPointer<PsiTypeParameter>, SmartTypePointer>> set = myMap.entrySet();
            for (Map.Entry<SmartPsiElementPointer<PsiTypeParameter>, SmartTypePointer> entry : set) {
                PsiTypeParameter element = entry.getKey().getElement();
                if (element != null) {
                    SmartTypePointer typePointer = entry.getValue();
                    resurrected.put(element, typePointer == null ? null : typePointer.getType());
                }
            }
            for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(classElement)) {
                if (!resurrected.containsKey(typeParameter)) {
                    resurrected.put(typeParameter, null);
                }
            }
            PsiSubstitutor resurrectedSubstitutor = PsiSubstitutor.createSubstitutor(resurrected);

            PsiAnnotation[] resurrectedAnnotations = Stream.of(myAnnotations)
                .map(SmartPsiElementPointer::getElement)
                .filter(Objects::nonNull)
                .toArray(PsiAnnotation[]::new);
            return new PsiImmediateClassType(classElement, resurrectedSubstitutor, myLevel, resurrectedAnnotations);
        }
    }

    private class DisjunctionTypePointer extends TypePointerBase<PsiDisjunctionType> {
        private final List<SmartTypePointer> myPointers;

        private DisjunctionTypePointer(@Nonnull PsiDisjunctionType type) {
            super(type);
            myPointers = ContainerUtil.map(type.getDisjunctions(), SmartTypePointerManagerImpl.this::createSmartTypePointer);
        }

        @Override
        protected PsiDisjunctionType calcType() {
            List<PsiType> types = ContainerUtil.map(myPointers, SmartTypePointer::getType);
            return new PsiDisjunctionType(types, PsiManager.getInstance(myProject));
        }
    }

    private class SmartTypeCreatingVisitor extends PsiTypeVisitor<SmartTypePointer> {
        @Override
        public SmartTypePointer visitPrimitiveType(PsiPrimitiveType primitiveType) {
            return new SimpleTypePointer(primitiveType);
        }

        @Override
        public SmartTypePointer visitArrayType(PsiArrayType arrayType) {
            SmartTypePointer componentTypePointer = arrayType.getComponentType().accept(this);
            return componentTypePointer != null ? new ArrayTypePointer(arrayType, componentTypePointer) : null;
        }

        @Override
        public SmartTypePointer visitWildcardType(PsiWildcardType wildcardType) {
            PsiType bound = wildcardType.getBound();
            SmartTypePointer boundPointer = bound == null ? null : bound.accept(this);
            return new WildcardTypePointer(wildcardType, boundPointer);
        }

        @Override
        public SmartTypePointer visitClassType(PsiClassType classType) {
            PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
            PsiClass aClass = resolveResult.getElement();
            if (aClass == null) {
                return createClassReferenceTypePointer(classType);
            }
            PsiSubstitutor substitutor = resolveResult.getSubstitutor();
            HashMap<SmartPsiElementPointer<PsiTypeParameter>, SmartTypePointer> pointerMap = new HashMap<>();
            Map<PsiTypeParameter, PsiType> map = new HashMap<>();
            for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
                PsiType substitutionResult = substitutor.substitute(typeParameter);
                if (substitutionResult != null) {
                    SmartPsiElementPointer<PsiTypeParameter> pointer =
                        myPsiPointerManager.createSmartPsiElementPointer(typeParameter);
                    SmartTypePointer typePointer = substitutionResult.accept(this);
                    pointerMap.put(pointer, typePointer);
                    map.put(typeParameter, typePointer.getType());
                }
                else {
                    map.put(typeParameter, null);
                }
            }

            SmartPsiElementPointer<?>[] annotationPointers =
                Stream
                    .of(classType.getAnnotations())
                    .map(myPsiPointerManager::createSmartPsiElementPointer)
                    .toArray(SmartPsiElementPointer<?>[]::new);

            LanguageLevel languageLevel = classType.getLanguageLevel();
            return new ClassTypePointer(
                new PsiImmediateClassType(
                    aClass,
                    PsiSubstitutor.createSubstitutor(map),
                    languageLevel,
                    classType.getAnnotations()
                ),
                myPsiPointerManager.createSmartPsiElementPointer(aClass),
                languageLevel,
                pointerMap,
                annotationPointers
            );
        }

        @Override
        public SmartTypePointer visitDisjunctionType(PsiDisjunctionType disjunctionType) {
            return new DisjunctionTypePointer(disjunctionType);
        }
    }

    @Nonnull
    private SmartTypePointer createClassReferenceTypePointer(@Nonnull PsiClassType classType) {
        for (ClassTypePointerFactory factory : ClassTypePointerFactory.EP_NAME.getExtensions()) {
            SmartTypePointer pointer = factory.createClassTypePointer(classType, myProject);
            if (pointer != null) {
                return pointer;
            }
        }

        return new SimpleTypePointer(classType);
    }

}
