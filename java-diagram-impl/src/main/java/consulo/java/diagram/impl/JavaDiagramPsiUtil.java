/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package consulo.java.diagram.impl;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiReferenceList;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public final class JavaDiagramPsiUtil {
    private JavaDiagramPsiUtil() {
    }

    public static Collection<PsiClass> getAllInnerClasses(PsiClass psiClass) {
        Set<PsiClass> inners = new HashSet<>();
        for (PsiClass inner : psiClass.getInnerClasses()) {
            if (inner.getName() != null) {
                inners.add(inner);
                inners.addAll(getAllInnerClasses(inner));
            }
        }
        return inners;
    }

    public static @Nullable PsiClass findAnnotationClass(@Nullable PsiAnnotation annotation) {
        if (annotation == null) {
            return null;
        }
        PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
        if (reference == null) {
            return null;
        }
        return reference.resolve() instanceof PsiClass psiClass ? psiClass : null;
    }

    public static Set<PsiClass> findAnnotationsForClass(@Nullable PsiClass psiClass) {
        Set<PsiClass> classes = new HashSet<>();
        if (psiClass == null) {
            return classes;
        }
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList == null) {
            return classes;
        }
        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            PsiClass annotationClass = findAnnotationClass(annotation);
            if (annotationClass != null) {
                classes.add(annotationClass);
            }
        }
        return classes;
    }

    public static @Nullable String createInheritanceBetween(PsiClass child, PsiClass parent) {
        if (child.isInheritor(parent, true) || parent.isInheritor(child, true)) {
            return "Relationship between " + child.getName() + " and " + parent.getName() + " already exists";
        }
        if (child.equals(parent)) {
            return null;
        }
        PsiModifierList modifiers = parent.getModifierList();
        if (modifiers == null) {
            return null;
        }
        if (child.isAnnotationType() && !parent.isAnnotationType()) {
            return "Annotations can't extend/implement other classes/interfaces";
        }
        if (modifiers.hasModifierProperty(PsiModifier.FINAL)) {
            return "Class " + parent.getName() + " is final";
        }

        PsiElementFactory factory = JavaPsiFacade.getInstance(child.getProject()).getElementFactory();
        if (parent.isInterface()) {
            PsiReferenceList implementsList = child.isInterface() ? child.getExtendsList() : child.getImplementsList();
            if (implementsList != null) {
                implementsList.add(factory.createClassReferenceElement(parent));
            }
        }
        else {
            if (child.isInterface()) {
                return "Interface can only extend an interface";
            }
            if (child.getSuperClass() != null) {
                PsiJavaCodeReferenceElement reference = factory.createClassReferenceElement(parent);
                PsiReferenceList extendsList = child.getExtendsList();
                if (extendsList != null) {
                    PsiJavaCodeReferenceElement[] elements = extendsList.getReferenceElements();
                    if (elements.length > 0) {
                        elements[0].replace(reference);
                    }
                    else {
                        extendsList.add(reference);
                    }
                }
            }
        }
        return null;
    }

    public static @Nullable String annotateClass(PsiClass psiClass, PsiClass annotation) {
        PsiModifierList modifierList = psiClass.getModifierList();
        String fqn = annotation.getQualifiedName();
        if (modifierList == null || fqn == null || modifierList.findAnnotation(fqn) != null) {
            return null;
        }

        PsiAnnotation psiAnnotation = JavaPsiFacade.getInstance(psiClass.getProject())
            .getElementFactory()
            .createAnnotationFromText("@" + fqn, psiClass);
        PsiElement first = modifierList.getFirstChild();
        if (first != null) {
            modifierList.addBefore(psiAnnotation, first);
        }
        else {
            modifierList.add(psiAnnotation);
        }
        return null;
    }

    public static void removeFromReferenceList(@Nullable PsiReferenceList list, PsiClass target) {
        if (list == null) {
            return;
        }
        PsiManager manager = target.getManager();
        for (PsiJavaCodeReferenceElement reference : list.getReferenceElements()) {
            if (manager.areElementsEquivalent(reference.resolve(), target)) {
                reference.delete();
            }
        }
    }
}
