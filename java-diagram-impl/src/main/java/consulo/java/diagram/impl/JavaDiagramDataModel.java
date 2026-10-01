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

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.diagram.DiagramDataModel;
import consulo.diagram.DiagramEdge;
import consulo.diagram.DiagramNode;
import consulo.diagram.DiagramNodesGroup;
import consulo.diagram.DiagramProvider;
import consulo.diagram.DiagramRelationshipInfo;
import consulo.diagram.DiagramRelationships;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.MessageBoxes;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public class JavaDiagramDataModel extends DiagramDataModel<PsiElement> {
    private final Project myProject;
    private final DiagramProvider<PsiElement> myProvider;
    private final @Nullable VirtualFile myEditorFile;
    private final SmartPointerManager mySmartPointerManager;

    private final Map<String, SmartPsiElementPointer<PsiClass>> myClassesAddedByUser = new HashMap<>();
    private final Map<String, SmartPsiElementPointer<PsiClass>> myClassesRemovedByUser = new HashMap<>();
    private final Map<String, SmartPsiElementPointer<PsiJavaPackage>> myPackages = new HashMap<>();
    private final Map<String, SmartPsiElementPointer<PsiJavaPackage>> myPackagesRemovedByUser = new HashMap<>();
    private final Map<String, JavaDiagramNodesGroup> myGroups = new HashMap<>();
    private @Nullable SmartPsiElementPointer<PsiJavaPackage> myInitialPackage;
    private @Nullable SmartPsiElementPointer<PsiElement> myInitialElement;
    private boolean myUseInnerClasses;

    private final Collection<DiagramNode<PsiElement>> myNodes = new LinkedHashSet<>();
    private final Collection<DiagramEdge<PsiElement>> myEdges = new LinkedHashSet<>();

    @RequiredReadAction
    public JavaDiagramDataModel(Project project, DiagramProvider<PsiElement> provider, @Nullable PsiElement psiElement, @Nullable VirtualFile file) {
        myProject = project;
        myProvider = provider;
        myEditorFile = file;
        mySmartPointerManager = SmartPointerManager.getInstance(project);

        if (psiElement == null) {
            return;
        }

        myInitialElement = mySmartPointerManager.createSmartPsiElementPointer(psiElement);

        if (psiElement instanceof PsiClass psiClass) {
            for (PsiClass aClass : getAllParentsForClass(psiClass)) {
                addClassPointer(aClass);
            }
        }

        if (psiElement instanceof PsiJavaPackage psiPackage) {
            myInitialPackage = mySmartPointerManager.createSmartPsiElementPointer(psiPackage);
            for (PsiClass psiClass : psiPackage.getClasses()) {
                addClassPointer(psiClass);
            }
            for (PsiJavaPackage subPackage : psiPackage.getSubPackages()) {
                myPackages.put(subPackage.getQualifiedName(), mySmartPointerManager.createSmartPsiElementPointer(subPackage));
            }
        }
    }

    private void addClassPointer(PsiClass psiClass) {
        String fqn = psiClass.getQualifiedName();
        if (fqn != null) {
            myClassesAddedByUser.put(fqn, mySmartPointerManager.createSmartPsiElementPointer(psiClass));
        }
    }

    @Override
    @RequiredReadAction
    public Collection<DiagramNode<PsiElement>> getNodes() {
        refreshDataModel();
        return myNodes;
    }

    @Override
    public Collection<DiagramEdge<PsiElement>> getEdges() {
        return myEdges;
    }

    @Override
    public DiagramNode<PsiElement> getSourceNode(DiagramEdge<PsiElement> edge) {
        return edge.getSource();
    }

    @Override
    public DiagramNode<PsiElement> getTargetNode(DiagramEdge<PsiElement> edge) {
        return edge.getTarget();
    }

    @Override
    public String getNodeName(DiagramNode<PsiElement> node) {
        PsiElement element = node.getIdentifyingElement();
        if (element instanceof PsiClass psiClass) {
            return String.valueOf(psiClass.getName());
        }
        if (element instanceof PsiJavaPackage psiPackage) {
            return "Package " + psiPackage.getQualifiedName();
        }
        return "";
    }

    @Override
    public String getEdgeName(DiagramEdge<PsiElement> edge) {
        return edge.getName();
    }

    @Override
    public @Nullable DiagramNodesGroup getGroup(DiagramNode<PsiElement> node) {
        if (!(node.getIdentifyingElement() instanceof PsiClass psiClass)) {
            return null;
        }
        String packageName = JavaDiagramUtil.getRealPackageName(psiClass);
        return myGroups.computeIfAbsent(packageName == null ? "" : packageName, JavaDiagramNodesGroup::new);
    }

    @Override
    public @Nullable DiagramEdge<PsiElement> createEdge(DiagramNode<PsiElement> from, DiagramNode<PsiElement> to) {
        if (!(from.getIdentifyingElement() instanceof PsiClass child) || !(to.getIdentifyingElement() instanceof PsiClass parent)) {
            return null;
        }

        if (!FileModificationService.getInstance().prepareFileForWrite(child.getContainingFile())) {
            return null;
        }

        Supplier<@Nullable String> link = () -> parent.isAnnotationType()
            ? JavaDiagramPsiUtil.annotateClass(child, parent)
            : JavaDiagramPsiUtil.createInheritanceBetween(child, parent);
        String error = WriteCommandAction.runWriteCommandAction(myProject, link);

        if (error != null) {
            MessageBoxes.okError(LocalizeValue.of(error)).title(LocalizeValue.localizeTODO("Cannot create relationship link")).showAsync();
            return null;
        }

        DiagramRelationshipInfo relationship;
        if (parent.isInterface()) {
            if (parent.isAnnotationType()) {
                relationship = DiagramRelationships.ANNOTATION;
            }
            else {
                relationship = child.isInterface() ? DiagramRelationships.INTERFACE_GENERALIZATION : DiagramRelationships.REALIZATION;
            }
        }
        else {
            relationship = DiagramRelationships.GENERALIZATION;
        }

        JavaDiagramEdge edge = new JavaDiagramEdge(from, to, relationship);
        myEdges.add(edge);
        return edge;
    }

    @Override
    public void removeNode(DiagramNode<PsiElement> node) {
        removeElement(node.getIdentifyingElement());
    }

    @Override
    public void removeEdge(DiagramEdge<PsiElement> edge) {
        PsiElement source = edge.getSource().getIdentifyingElement();
        PsiElement target = edge.getTarget().getIdentifyingElement();
        DiagramRelationshipInfo relationship = edge.getRelationship();
        if (!(source instanceof PsiClass src) || !(target instanceof PsiClass trg) || relationship == DiagramRelationshipInfo.NO_RELATIONSHIP) {
            return;
        }

        if (!FileModificationService.getInstance().prepareFileForWrite(src.getContainingFile())) {
            return;
        }

        MessageBoxes.yesNo()
            .asQuestion()
            .title(LocalizeValue.localizeTODO("Remove relationship link"))
            .text(getMessage(src, trg, relationship))
            .showAsync()
            .whenComplete((confirmed, throwable) -> {
                if (!Boolean.TRUE.equals(confirmed)) {
                    return;
                }

                WriteCommandAction.runWriteCommandAction(myProject, () -> {
                    if (relationship == DiagramRelationships.GENERALIZATION) {
                        JavaDiagramPsiUtil.removeFromReferenceList(src.getExtendsList(), trg);
                    }
                    else if (relationship == DiagramRelationships.REALIZATION || relationship == DiagramRelationships.INTERFACE_GENERALIZATION) {
                        JavaDiagramPsiUtil.removeFromReferenceList(src.isInterface() ? src.getExtendsList() : src.getImplementsList(), trg);
                    }
                    else if (relationship == DiagramRelationships.ANNOTATION) {
                        PsiModifierList list = src.getModifierList();
                        if (list != null) {
                            for (PsiAnnotation annotation : list.getAnnotations()) {
                                if (annotation.isPhysical() && annotation.isValid()
                                    && Objects.equals(annotation.getQualifiedName(), trg.getQualifiedName())) {
                                    annotation.delete();
                                }
                            }
                        }
                    }
                });
                myEdges.remove(edge);
            });
    }

    private static LocalizeValue getMessage(PsiClass source, PsiClass target, DiagramRelationshipInfo relationship) {
        if (relationship == DiagramRelationships.ANNOTATION) {
            return LocalizeValue.localizeTODO("Remove annotation @" + target.getName() + " from " + source.getName());
        }
        return LocalizeValue.localizeTODO("This will remove relationship link between classes and modify class " + source.getQualifiedName() + ". Continue?");
    }

    @RequiredReadAction
    private void refreshDataModel() {
        myNodes.clear();
        myEdges.clear();
        updateDataModel();
    }

    @RequiredReadAction
    private boolean isAllowedToShow(@Nullable PsiClass psiClass) {
        if (psiClass == null || !psiClass.isValid()) {
            return false;
        }
        for (SmartPsiElementPointer<PsiClass> pointer : myClassesRemovedByUser.values()) {
            if (psiClass.equals(pointer.getElement())) {
                return false;
            }
        }
        if (getInitialElement() instanceof PsiClass initialClass && sameClass(psiClass, initialClass)) {
            return true;
        }
        if (isInsidePackages(psiClass)) {
            return false;
        }
        return myUseInnerClasses || !PsiUtil.isInnerClass(psiClass);
    }

    private static boolean sameClass(@Nullable PsiClass one, @Nullable PsiClass another) {
        return one != null && one.isValid()
            && another != null && another.isValid()
            && Objects.equals(one.getQualifiedName(), another.getQualifiedName());
    }

    @RequiredReadAction
    private void updateDataModel() {
        Set<PsiClass> classes = getAllClasses();
        syncPackages();

        Set<String> interfaces = new HashSet<>();
        Set<String> annotations = new HashSet<>();

        for (SmartPsiElementPointer<PsiJavaPackage> pointer : myPackages.values()) {
            PsiJavaPackage psiPackage = pointer.getElement();
            if (psiPackage != null) {
                myNodes.add(new JavaDiagramNode(psiPackage, myProvider));
            }
        }

        for (PsiClass psiClass : classes) {
            if (isAllowedToShow(psiClass)) {
                myNodes.add(new JavaDiagramNode(psiClass, myProvider));
            }
            if (psiClass.isAnnotationType()) {
                annotations.add(psiClass.getQualifiedName());
            }
            else if (psiClass.isInterface()) {
                interfaces.add(psiClass.getQualifiedName());
            }
        }

        for (PsiClass psiClass : classes) {
            if (isGeneralizationEdgeAllowed(psiClass)) {
                DiagramNode<PsiElement> source = findNode(psiClass);
                DiagramNode<PsiElement> target = null;
                PsiClass superClass = psiClass.getSuperClass();
                while (target == null && superClass != null) {
                    target = findNode(superClass);
                    superClass = superClass.getSuperClass();
                }
                if (source != null && target != null && source != target) {
                    addEdge(source, target, DiagramRelationships.GENERALIZATION);
                }
            }

            for (PsiClass inter : psiClass.getInterfaces()) {
                if (interfaces.contains(inter.getQualifiedName())) {
                    DiagramNode<PsiElement> source = findNode(psiClass);
                    DiagramNode<PsiElement> target = findNode(inter);
                    if (source != null && target != null && source != target) {
                        addEdge(source, target, psiClass.isInterface() ? DiagramRelationships.INTERFACE_GENERALIZATION : DiagramRelationships.REALIZATION);
                    }
                }
            }

            if (psiClass.isInterface()) {
                Set<PsiClass> found = new HashSet<>();
                findNearestInterfaces(psiClass, interfaces, found);
                for (PsiClass inter : found) {
                    DiagramNode<PsiElement> source = findNode(psiClass);
                    DiagramNode<PsiElement> target = findNode(inter);
                    if (source != null && target != null && source != target) {
                        addEdge(source, target, DiagramRelationships.INTERFACE_GENERALIZATION);
                    }
                }
            }
            else {
                Set<PsiClass> inters = new HashSet<>(Arrays.asList(psiClass.getInterfaces()));
                PsiClass current = psiClass.getSuperClass();
                while (current != null) {
                    if (findNode(current) != null) {
                        break;
                    }
                    inters.addAll(Arrays.asList(current.getInterfaces()));
                    current = current.getSuperClass();
                }

                List<PsiClass> faces = new ArrayList<>(inters);
                Set<PsiClass> visited = new HashSet<>();
                while (!faces.isEmpty()) {
                    PsiClass inter = faces.remove(0);
                    if (!visited.add(inter)) {
                        continue;
                    }
                    if (findNode(inter) != null) {
                        DiagramNode<PsiElement> source = findNode(psiClass);
                        DiagramNode<PsiElement> target = findNode(inter);
                        if (source != null && target != null && source != target) {
                            addEdge(source, target, DiagramRelationships.REALIZATION);
                        }
                    }
                    else {
                        faces.addAll(Arrays.asList(inter.getInterfaces()));
                    }
                }
            }

            if (!isInsidePackages(psiClass) && myUseInnerClasses) {
                for (PsiClass inner : psiClass.getInnerClasses()) {
                    if (classes.contains(inner)) {
                        DiagramNode<PsiElement> source = findNode(inner);
                        DiagramNode<PsiElement> target = findNode(psiClass);
                        if (source != null && target != null && source != target) {
                            addEdge(source, target, DiagramRelationships.INNER_CLASS);
                        }
                    }
                }
            }

            for (PsiClass annotation : JavaDiagramPsiUtil.findAnnotationsForClass(psiClass)) {
                if (annotations.contains(annotation.getQualifiedName())) {
                    DiagramNode<PsiElement> source = findNode(psiClass);
                    DiagramNode<PsiElement> target = findNode(annotation);
                    if (source != null && target != null && source != target) {
                        addEdge(source, target, DiagramRelationships.ANNOTATION);
                    }
                }
            }
        }
    }

    @RequiredReadAction
    private void syncPackages() {
        SmartPsiElementPointer<PsiJavaPackage> initialPackage = myInitialPackage;
        PsiJavaPackage initPackage = initialPackage == null ? null : initialPackage.getElement();
        if (initPackage == null) {
            return;
        }

        Map<String, PsiJavaPackage> psiPackages = new HashMap<>();
        for (PsiJavaPackage sub : initPackage.getSubPackages()) {
            psiPackages.put(sub.getQualifiedName(), sub);
        }
        for (String fqn : myPackages.keySet()) {
            psiPackages.remove(fqn);
        }
        for (String fqn : myPackagesRemovedByUser.keySet()) {
            psiPackages.remove(fqn);
        }
        for (PsiJavaPackage psiPackage : psiPackages.values()) {
            myPackages.put(psiPackage.getQualifiedName(), mySmartPointerManager.createSmartPsiElementPointer(psiPackage));
        }
    }

    private static void findNearestInterfaces(PsiClass psiClass, Set<String> interfaces, Set<PsiClass> found) {
        for (PsiClass inter : psiClass.getInterfaces()) {
            if (interfaces.contains(inter.getQualifiedName())) {
                found.add(inter);
            }
            else {
                findNearestInterfaces(inter, interfaces, found);
            }
        }
    }

    private static boolean isGeneralizationEdgeAllowed(PsiClass psiClass) {
        return !psiClass.isInterface() && !psiClass.isAnnotationType();
    }

    private boolean isInsidePackages(PsiClass psiClass) {
        String packageName = JavaDiagramUtil.getRealPackageName(psiClass);
        return packageName != null && myPackages.get(packageName) != null;
    }

    private void addEdge(DiagramNode<PsiElement> from, DiagramNode<PsiElement> to, DiagramRelationshipInfo relationship) {
        for (DiagramEdge<PsiElement> edge : myEdges) {
            if (edge.getSource() == from && edge.getTarget() == to && edge.getRelationship() == relationship) {
                return;
            }
        }
        myEdges.add(new JavaDiagramEdge(from, to, relationship));
    }

    @RequiredReadAction
    private Set<PsiClass> getAllClasses() {
        Set<PsiClass> classes = new HashSet<>();
        for (SmartPsiElementPointer<PsiClass> pointer : myClassesAddedByUser.values()) {
            PsiClass psiClass = pointer.getElement();
            if (psiClass != null) {
                classes.add(psiClass);
            }
        }

        SmartPsiElementPointer<PsiJavaPackage> initialPackage = myInitialPackage;
        PsiJavaPackage initPackage = initialPackage == null ? null : initialPackage.getElement();
        if (initPackage != null) {
            classes.addAll(Arrays.asList(initPackage.getClasses()));
        }

        for (SmartPsiElementPointer<PsiJavaPackage> pointer : myPackages.values()) {
            PsiJavaPackage psiPackage = pointer.getElement();
            if (psiPackage != null) {
                classes.addAll(Arrays.asList(psiPackage.getClasses()));
            }
        }

        if (myUseInnerClasses) {
            Set<PsiClass> inners = new HashSet<>();
            for (PsiClass aClass : classes) {
                inners.addAll(JavaDiagramPsiUtil.getAllInnerClasses(aClass));
            }
            classes.addAll(inners);
        }

        classes.removeIf(aClass -> !aClass.isValid());
        for (SmartPsiElementPointer<PsiClass> pointer : myClassesRemovedByUser.values()) {
            classes.remove(pointer.getElement());
        }
        return classes;
    }

    private static Set<PsiClass> getAllParentsForClass(PsiClass psiClass) {
        return findAllParentsForClass(psiClass, new HashSet<>());
    }

    private static Set<PsiClass> findAllParentsForClass(PsiClass clazz, Set<PsiClass> found) {
        found.add(clazz);
        for (PsiClass psiClass : clazz.getSupers()) {
            if (psiClass.getSuperClass() != null) {
                if (found.add(psiClass)) {
                    findAllParentsForClass(psiClass, found);
                }
            }
            else if (!clazz.isInterface()) {
                found.add(psiClass);
            }
        }

        PsiModifierList modifierList = clazz.getModifierList();
        if (modifierList != null) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                PsiClass annotationClass = JavaDiagramPsiUtil.findAnnotationClass(annotation);
                if (annotationClass != null) {
                    found.add(annotationClass);
                }
            }
        }
        return found;
    }

    public @Nullable DiagramNode<PsiElement> findNode(@Nullable PsiElement psiElement) {
        if (psiElement == null) {
            return null;
        }
        String fqn = JavaDiagramUtil.getFQN(psiElement);
        if (fqn != null) {
            for (DiagramNode<PsiElement> node : myNodes) {
                if (fqn.equals(JavaDiagramUtil.getFQN(node.getIdentifyingElement()))) {
                    return node;
                }
            }
        }

        String packageName = JavaDiagramUtil.getPackageName(psiElement);
        SmartPsiElementPointer<PsiJavaPackage> pointer = packageName == null ? null : myPackages.get(packageName);
        PsiJavaPackage psiPackage = pointer == null ? null : pointer.getElement();
        return psiPackage == null || psiPackage == psiElement ? null : findNode(psiPackage);
    }

    public void removeElement(PsiElement element) {
        DiagramNode<PsiElement> node = findNode(element);
        if (node == null) {
            String fqn = JavaDiagramUtil.getFQN(element);
            if (fqn != null) {
                myClassesAddedByUser.remove(fqn);
            }
            return;
        }

        myEdges.removeIf(edge -> edge.getTarget() == node || edge.getSource() == node);
        myNodes.remove(node);

        if (element instanceof PsiClass psiClass) {
            removeClass(psiClass);
            for (PsiClass innerClass : psiClass.getInnerClasses()) {
                removeClass(innerClass);
            }
        }

        if (element instanceof PsiJavaPackage psiPackage) {
            String qualifiedName = psiPackage.getQualifiedName();
            myPackages.remove(qualifiedName);
            myPackagesRemovedByUser.put(qualifiedName, mySmartPointerManager.createSmartPsiElementPointer(psiPackage));
            myClassesAddedByUser.values().removeIf(pointer -> {
                PsiClass psiClass = pointer.getElement();
                return psiClass == null || Objects.equals(qualifiedName, JavaDiagramUtil.getRealPackageName(psiClass));
            });
        }
    }

    private void removeClass(PsiClass psiClass) {
        String fqn = psiClass.getQualifiedName();
        if (fqn != null) {
            myClassesRemovedByUser.put(fqn, mySmartPointerManager.createSmartPsiElementPointer(psiClass));
            myClassesAddedByUser.remove(fqn);
        }
    }

    @Override
    public @Nullable DiagramNode<PsiElement> addElement(PsiElement element) {
        if (findNode(element) != null) {
            return null;
        }

        if (element instanceof PsiJavaPackage psiPackage) {
            String fqn = psiPackage.getQualifiedName();
            if (fqn == null || fqn.isEmpty()) {
                return null;
            }
            myPackages.put(fqn, mySmartPointerManager.createSmartPsiElementPointer(psiPackage));
            myPackagesRemovedByUser.remove(fqn);
        }
        else if (element instanceof PsiClass psiClass) {
            String fqn = psiClass.getQualifiedName();
            if (fqn == null || isInsidePackages(psiClass)) {
                return null;
            }
            myClassesAddedByUser.put(fqn, mySmartPointerManager.createSmartPsiElementPointer(psiClass));
            myClassesRemovedByUser.remove(fqn);
        }
        else {
            return null;
        }

        JavaDiagramNode node = new JavaDiagramNode(element, myProvider);
        myNodes.add(node);
        return node;
    }

    public void setUseInnerClasses(boolean useInnerClasses) {
        myUseInnerClasses = useInnerClasses;
    }

    public @Nullable PsiElement getInitialElement() {
        SmartPsiElementPointer<PsiElement> initialElement = myInitialElement;
        PsiElement element = initialElement == null ? null : initialElement.getElement();
        return element == null || !element.isValid() ? null : element;
    }

    public @Nullable VirtualFile getFile() {
        return myEditorFile;
    }

    @Override
    public boolean hasElement(PsiElement element) {
        return findNode(element) != null;
    }

    @Override
    public void dispose() {
    }
}
