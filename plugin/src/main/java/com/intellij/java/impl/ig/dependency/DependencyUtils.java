/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.dependency;

import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefJavaElement;
import com.intellij.java.analysis.codeInspection.reference.RefJavaUtil;
import com.intellij.java.analysis.codeInspection.reference.RefPackage;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.util.dataholder.Key;

import java.util.*;

public class DependencyUtils {

  private static final Key<Set<RefClass>> DEPENDENT_CLASSES_KEY =
      new Key<Set<RefClass>>("DEPENDENT_CLASSES");
  private static final Key<Set<RefClass>> DEPENDENCY_CLASSES_KEY =
      new Key<Set<RefClass>>("DEPENDENCY_CLASSES");
  private static final Key<Set<RefClass>> TRANSITIVE_DEPENDENT_CLASSES_KEY =
      new Key<Set<RefClass>>("TRANSITIVE_DEPENDENT_CLASSES");
  private static final Key<Set<RefClass>> TRANSITIVE_DEPENDENCY_CLASSES_KEY =
      new Key<Set<RefClass>>("TRANSITIVE_DEPENDENCY_CLASSES");

  private static final Key<Set<RefPackage>> DEPENDENT_PACKAGES_KEY =
      new Key<Set<RefPackage>>("DEPENDENT_PACKAGES");
  private static final Key<Set<RefPackage>> DEPENDENCY_PACKAGES_KEY =
      new Key<Set<RefPackage>>("DEPENDENCY_PACKAGES");
  private static final Key<Set<RefPackage>> TRANSITIVE_DEPENDENT_PACKAGES_KEY =
      new Key<Set<RefPackage>>("TRANSITIVE_DEPENDENT_PACKAGES");
  private static final Key<Set<RefPackage>> TRANSITIVE_DEPENDENCY_PACKAGES_KEY =
      new Key<Set<RefPackage>>("TRANSITIVE_DEPENDENCY_PACKAGES");

  private DependencyUtils() {
  }

  public static Set<RefClass> calculateDependenciesForClass(
      RefClass refClass) {
    Set<RefClass> dependencies =
        refClass.getUserData(DEPENDENCY_CLASSES_KEY);
    if (dependencies != null) {
      return dependencies;
    }
    Set<RefClass> newDependencies = new HashSet<RefClass>();
    tabulateDependencyClasses(refClass, newDependencies);
    newDependencies.remove(refClass);
    refClass.putUserData(DEPENDENCY_CLASSES_KEY, newDependencies);
    return newDependencies;
  }

  @SuppressWarnings({"MethodWithMultipleLoops"})
  static void tabulateDependencyClasses(RefJavaElement element,
                                        Set<RefClass> dependencies) {
    Collection<RefElement> references = element.getOutReferences();
    RefJavaUtil refUtil = RefJavaUtil.getInstance();
    for (RefElement reference : references) {
      RefClass refClass = refUtil.getTopLevelClass(reference);
      if (refClass != null) {
        dependencies.add(refClass);
      }
    }
    Collection<RefClass> typeReferences =
        element.getOutTypeReferences();
    for (RefElement reference : typeReferences) {
      RefClass refClass = refUtil.getTopLevelClass(reference);
      if (refClass != null) {
        dependencies.add(refClass);
      }
    }
    List<RefEntity> children = element.getChildren();
    if (children == null) {
      return;
    }
    for (RefEntity child : children) {
      if (child instanceof RefJavaElement) {
        tabulateDependencyClasses((RefJavaElement) child, dependencies);
      }
    }
  }

  public static Set<RefClass> calculateTransitiveDependenciesForClass(
      RefClass refClass) {
    Set<RefClass> dependencies =
        refClass.getUserData(TRANSITIVE_DEPENDENCY_CLASSES_KEY);
    if (dependencies != null) {
      return dependencies;
    }
    Set<RefClass> newDependencies = new HashSet<RefClass>();
    tabulateTransitiveDependencyClasses(refClass, newDependencies);
    refClass.putUserData(TRANSITIVE_DEPENDENCY_CLASSES_KEY, newDependencies);
    return newDependencies;
  }

  private static void tabulateTransitiveDependencyClasses(
      RefClass refClass, Set<RefClass> newDependencies) {
    LinkedList<RefClass> pendingClasses = new LinkedList<RefClass>();
    Set<RefClass> processedClasses = new HashSet<RefClass>();
    pendingClasses.addLast(refClass);
    while (!pendingClasses.isEmpty()) {
      RefClass classToProcess = pendingClasses.removeFirst();
      newDependencies.add(classToProcess);
      processedClasses.add(classToProcess);
      Set<RefClass> dependencies =
          calculateDependenciesForClass(classToProcess);
      for (RefClass dependency : dependencies) {
        if (!pendingClasses.contains(dependency) &&
            !processedClasses.contains(dependency)) {
          pendingClasses.addLast(dependency);
        }
      }
    }
    newDependencies.remove(refClass);
  }

  public static Set<RefClass> calculateDependentsForClass(RefClass refClass) {
    Set<RefClass> dependents =
        refClass.getUserData(DEPENDENT_CLASSES_KEY);
    if (dependents != null) {
      return dependents;
    }
    Set<RefClass> newDependents = new HashSet<RefClass>();
    tabulateDependentClasses(refClass, newDependents);
    Set<RefElement> typeReferences = refClass.getInTypeReferences();
    RefJavaUtil refUtil = RefJavaUtil.getInstance();
    for (RefElement typeReference : typeReferences) {
      RefClass referencingClass =
          refUtil.getTopLevelClass(typeReference);
      newDependents.add(referencingClass);
    }
    newDependents.remove(refClass);
    refClass.putUserData(DEPENDENT_CLASSES_KEY, newDependents);
    return newDependents;
  }

  @SuppressWarnings({"MethodWithMultipleLoops"})
  private static void tabulateDependentClasses(RefElement element,
                                               Set<RefClass> dependents) {
    Collection<RefElement> references = element.getInReferences();
    RefJavaUtil refUtil = RefJavaUtil.getInstance();
    for (RefElement reference : references) {
      RefClass refClass = refUtil.getTopLevelClass(reference);
      if (refClass != null) {
        dependents.add(refClass);
      }
    }
    List<RefEntity> children = element.getChildren();
    if (children == null) {
      return;
    }
    for (RefEntity child : children) {
      if (child instanceof RefElement) {
        tabulateDependentClasses((RefElement) child, dependents);
      }
    }
  }

  public static Set<RefClass> calculateTransitiveDependentsForClass(
      RefClass refClass) {
    Set<RefClass> dependents =
        refClass.getUserData(TRANSITIVE_DEPENDENT_CLASSES_KEY);
    if (dependents != null) {
      return dependents;
    }
    Set<RefClass> newDependents = new HashSet<RefClass>();
    tabulateTransitiveDependentClasses(refClass, newDependents);
    refClass.putUserData(TRANSITIVE_DEPENDENT_CLASSES_KEY, newDependents);
    return newDependents;
  }

  private static void tabulateTransitiveDependentClasses(
      RefClass refClass, Set<RefClass> newDependents) {
    LinkedList<RefClass> pendingClasses = new LinkedList<RefClass>();
    Set<RefClass> processedClasses = new HashSet<RefClass>();
    pendingClasses.addLast(refClass);
    while (!pendingClasses.isEmpty()) {
      RefClass classToProcess = pendingClasses.removeFirst();
      newDependents.add(classToProcess);
      processedClasses.add(classToProcess);
      Set<RefClass> dependents =
          calculateDependentsForClass(classToProcess);
      for (RefClass dependent : dependents) {
        if (!pendingClasses.contains(dependent) &&
            !processedClasses.contains(dependent)) {
          pendingClasses.addLast(dependent);
        }
      }
    }
    newDependents.remove(refClass);
  }

  public static Set<RefPackage> calculateDependenciesForPackage(
      RefPackage refPackage) {
    Set<RefPackage> dependencies =
        refPackage.getUserData(DEPENDENCY_PACKAGES_KEY);
    if (dependencies != null) {
      return dependencies;
    }
    Set<RefPackage> newDependencies = new HashSet<RefPackage>();

    tabulateDependencyPackages(refPackage, newDependencies);
    newDependencies.remove(refPackage);
    refPackage.putUserData(DEPENDENCY_PACKAGES_KEY, newDependencies);
    return newDependencies;
  }

  static void tabulateDependencyPackages(RefEntity entity,
                                         Set<RefPackage> dependencies) {
    if (entity instanceof RefElement) {
      RefElement element = (RefElement) entity;
      Collection<RefElement> references = element.getOutReferences();
      for (RefElement reference : references) {
        RefPackage refPackage = RefJavaUtil.getPackage(reference);
        if (refPackage != null) {
          dependencies.add(refPackage);
        }
      }
    }
    List<RefEntity> children = entity.getChildren();
    if (children == null) {
      return;
    }
    for (RefEntity child : children) {
      if (!(child instanceof RefPackage)) {
        tabulateDependencyPackages(child, dependencies);
      }
    }
  }

  public static Set<RefPackage> calculateDependentsForPackage(
      RefPackage refPackage) {
    Set<RefPackage> dependents =
        refPackage.getUserData(DEPENDENT_PACKAGES_KEY);
    if (dependents != null) {
      return dependents;
    }
    Set<RefPackage> newDependents = new HashSet<RefPackage>();
    tabulateDependentPackages(refPackage, newDependents);
    newDependents.remove(refPackage);
    refPackage.putUserData(DEPENDENT_PACKAGES_KEY, newDependents);
    return newDependents;
  }

  static void tabulateDependentPackages(RefEntity entity,
                                        Set<RefPackage> dependents) {
    if (entity instanceof RefElement) {
      RefElement element = (RefElement) entity;
      Collection<RefElement> references = element.getOutReferences();
      for (RefElement reference : references) {
        RefPackage refPackage = RefJavaUtil.getPackage(reference);
        if (refPackage != null) {
          dependents.add(refPackage);
        }
      }
    }
    List<RefEntity> children = entity.getChildren();
    if (children == null) {
      return;
    }
    for (RefEntity child : children) {
      if (!(child instanceof RefPackage)) {
        tabulateDependentPackages(child, dependents);
      }
    }
  }

  public static Set<RefPackage> calculateTransitiveDependentsForPackage(
      RefPackage refPackage) {
    Set<RefPackage> dependents =
        refPackage.getUserData(TRANSITIVE_DEPENDENT_PACKAGES_KEY);
    if (dependents != null) {
      return dependents;
    }
    Set<RefPackage> newDependents = new HashSet<RefPackage>();
    tabulateTransitiveDependentPackages(refPackage, newDependents);
    refPackage.putUserData(TRANSITIVE_DEPENDENT_PACKAGES_KEY, newDependents);
    return newDependents;
  }

  private static void tabulateTransitiveDependentPackages(
      RefPackage refPackage, Set<RefPackage> newDependents) {
    LinkedList<RefPackage> pendingPackages =
        new LinkedList<RefPackage>();
    Set<RefPackage> processedPackages = new HashSet<RefPackage>();
    pendingPackages.addLast(refPackage);
    while (!pendingPackages.isEmpty()) {
      RefPackage packageToProcess = pendingPackages.removeFirst();
      newDependents.add(packageToProcess);
      processedPackages.add(packageToProcess);
      Set<RefPackage> dependents =
          calculateDependentsForPackage(packageToProcess);
      for (RefPackage dependent : dependents) {
        if (!pendingPackages.contains(dependent) &&
            !processedPackages.contains(dependent)) {
          pendingPackages.addLast(dependent);
        }
      }
    }
    newDependents.remove(refPackage);
  }

  public static Set<RefPackage> calculateTransitiveDependenciesForPackage(
      RefPackage refPackage) {
    Set<RefPackage> dependencies =
        refPackage.getUserData(TRANSITIVE_DEPENDENCY_PACKAGES_KEY);
    if (dependencies != null) {
      return dependencies;
    }
    Set<RefPackage> newDependencies = new HashSet<RefPackage>();
    tabulateTransitiveDependencyPackages(refPackage, newDependencies);
    refPackage.putUserData(TRANSITIVE_DEPENDENCY_PACKAGES_KEY,
        newDependencies);
    return newDependencies;
  }

  private static void tabulateTransitiveDependencyPackages(
      RefPackage refPackage, Set<RefPackage> newDependencies) {
    LinkedList<RefPackage> pendingPackages =
        new LinkedList<RefPackage>();
    Set<RefPackage> processedPackages = new HashSet<RefPackage>();
    pendingPackages.addLast(refPackage);
    while (!pendingPackages.isEmpty()) {
      RefPackage packageToProcess = pendingPackages.removeFirst();
      newDependencies.add(packageToProcess);
      processedPackages.add(packageToProcess);
      Set<RefPackage> dependencies =
          calculateDependenciesForPackage(packageToProcess);
      for (RefPackage dependency : dependencies) {
        if (!pendingPackages.contains(dependency) &&
            !processedPackages.contains(dependency)) {
          pendingPackages.addLast(dependency);
        }
      }
    }
    newDependencies.remove(refPackage);
  }
}