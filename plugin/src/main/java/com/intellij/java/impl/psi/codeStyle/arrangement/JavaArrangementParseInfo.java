/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.codeStyle.arrangement;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.ide.impl.idea.util.containers.ContainerUtilRt;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Stack;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import consulo.util.lang.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 9/18/12 11:11 AM
 */
public class JavaArrangementParseInfo {

  @Nonnull
  private final List<JavaElementArrangementEntry> myEntries = new ArrayList<JavaElementArrangementEntry>();

  @Nonnull
  private final Map<Pair<String/* property name */, String/* class name */>, JavaArrangementPropertyInfo> myProperties = new HashMap<Pair<String,
      String>, JavaArrangementPropertyInfo>();

  @Nonnull
  private final List<ArrangementEntryDependencyInfo> myMethodDependencyRoots = new ArrayList<ArrangementEntryDependencyInfo>();

  @Nonnull
  private final Map<PsiMethod /* anchor */, Set<PsiMethod /* dependencies */>> myMethodDependencies = new HashMap<PsiMethod, Set<PsiMethod>>();

  @Nonnull
  private final Map<PsiMethod, JavaElementArrangementEntry> myMethodEntriesMap = new HashMap<PsiMethod, JavaElementArrangementEntry>();

  @Nonnull
  private final Map<PsiClass, List<Pair<PsiMethod/*overridden*/, PsiMethod/*overriding*/>>> myOverriddenMethods = new LinkedHashMap<PsiClass,
      List<Pair<PsiMethod, PsiMethod>>>();

  @Nonnull
  private final Set<PsiMethod> myTmpMethodDependencyRoots = new LinkedHashSet<PsiMethod>();
  @Nonnull
  private final Set<PsiMethod> myDependentMethods = new HashSet<PsiMethod>();
  private boolean myRebuildMethodDependencies;

  @Nonnull
  private FieldDependenciesManager myFieldDependenciesManager = new FieldDependenciesManager();

  @Nonnull
  public List<JavaElementArrangementEntry> getEntries() {
    return myEntries;
  }

  public void addEntry(@Nonnull JavaElementArrangementEntry entry) {
    myEntries.add(entry);
  }

  @Nonnull
  public Collection<JavaArrangementPropertyInfo> getProperties() {
    return myProperties.values();
  }

  /**
   * @return list of method dependency roots, i.e. there is a possible case that particular method
   * {@link ArrangementEntryDependencyInfo#getDependentEntriesInfos() calls another method}, it calls other methods
   * and so forth
   */
  @Nonnull
  public List<ArrangementEntryDependencyInfo> getMethodDependencyRoots() {
    if (myRebuildMethodDependencies) {
      myMethodDependencyRoots.clear();
      Map<PsiMethod, ArrangementEntryDependencyInfo> cache = new HashMap<PsiMethod, ArrangementEntryDependencyInfo>();
      for (PsiMethod method : myTmpMethodDependencyRoots) {
        ArrangementEntryDependencyInfo info = buildMethodDependencyInfo(method, cache);
        if (info != null) {
          myMethodDependencyRoots.add(info);
        }
      }
      myRebuildMethodDependencies = false;
    }
    return myMethodDependencyRoots;
  }

  @Nullable
  private ArrangementEntryDependencyInfo buildMethodDependencyInfo(
      @Nonnull final PsiMethod method, @Nonnull Map<PsiMethod, ArrangementEntryDependencyInfo> cache) {
    JavaElementArrangementEntry entry = myMethodEntriesMap.get(method);
    if (entry == null) {
      return null;
    }
    ArrangementEntryDependencyInfo result = new ArrangementEntryDependencyInfo(entry);
    Stack<Pair<PsiMethod, ArrangementEntryDependencyInfo>> toProcess = new Stack<Pair<PsiMethod, ArrangementEntryDependencyInfo>>();
    toProcess.push(Pair.create(method, result));
    Set<PsiMethod> usedMethods = ContainerUtilRt.newHashSet();
    while (!toProcess.isEmpty()) {
      Pair<PsiMethod, ArrangementEntryDependencyInfo> pair = toProcess.pop();
      Set<PsiMethod> dependentMethods = myMethodDependencies.get(pair.first);
      if (dependentMethods == null) {
        continue;
      }
      usedMethods.add(pair.first);
      for (PsiMethod dependentMethod : dependentMethods) {
        if (usedMethods.contains(dependentMethod)) {
          // Prevent cyclic dependencies.
          return null;
        }
        JavaElementArrangementEntry dependentEntry = myMethodEntriesMap.get(dependentMethod);
        if (dependentEntry == null) {
          continue;
        }
        ArrangementEntryDependencyInfo dependentMethodInfo = cache.get(dependentMethod);
        if (dependentMethodInfo == null) {
          cache.put(dependentMethod, dependentMethodInfo = new ArrangementEntryDependencyInfo(dependentEntry));
        }
        Pair<PsiMethod, ArrangementEntryDependencyInfo> dependentPair = Pair.create(dependentMethod, dependentMethodInfo);
        pair.second.addDependentEntryInfo(dependentPair.second);
        toProcess.push(dependentPair);
      }
    }
    return result;
  }

  public void registerGetter(@Nonnull String propertyName, @Nonnull String className, @Nonnull JavaElementArrangementEntry entry) {
    getPropertyInfo(propertyName, className).setGetter(entry);
  }

  public void registerSetter(@Nonnull String propertyName, @Nonnull String className, @Nonnull JavaElementArrangementEntry entry) {
    getPropertyInfo(propertyName, className).setSetter(entry);
  }

  @Nonnull
  private JavaArrangementPropertyInfo getPropertyInfo(@Nonnull String propertyName, @Nonnull String className) {
    Pair<String, String> key = Pair.create(propertyName, className);
    JavaArrangementPropertyInfo propertyInfo = myProperties.get(key);
    if (propertyInfo == null) {
      myProperties.put(key, propertyInfo = new JavaArrangementPropertyInfo());
    }
    return propertyInfo;
  }

  public void onMethodEntryCreated(@Nonnull PsiMethod method, @Nonnull JavaElementArrangementEntry entry) {
    myMethodEntriesMap.put(method, entry);
  }

  public void onFieldEntryCreated(@Nonnull PsiField field, @Nonnull JavaElementArrangementEntry entry) {
    myFieldDependenciesManager.registerFieldAndEntry(field, entry);
  }

  public void onOverriddenMethod(@Nonnull PsiMethod baseMethod, @Nonnull PsiMethod overridingMethod) {
    PsiClass clazz = baseMethod.getContainingClass();
    if (clazz == null) {
      return;
    }
    List<Pair<PsiMethod, PsiMethod>> methods = myOverriddenMethods.get(clazz);
    if (methods == null) {
      myOverriddenMethods.put(clazz, methods = new ArrayList<Pair<PsiMethod, PsiMethod>>());
    }
    methods.add(Pair.create(baseMethod, overridingMethod));
  }

  @Nonnull
  public List<JavaArrangementOverriddenMethodsInfo> getOverriddenMethods() {
    List<JavaArrangementOverriddenMethodsInfo> result = new ArrayList<JavaArrangementOverriddenMethodsInfo>();
    final ObjectIntMap<PsiMethod> weights = ObjectMaps.newObjectIntHashMap();
    Comparator<Pair<PsiMethod, PsiMethod>> comparator = (o1, o2) -> weights.getInt(o1.first) - weights.getInt(o2.first);
    for (Map.Entry<PsiClass, List<Pair<PsiMethod, PsiMethod>>> entry : myOverriddenMethods.entrySet()) {
      JavaArrangementOverriddenMethodsInfo info = new JavaArrangementOverriddenMethodsInfo(entry.getKey().getName());
      weights.clear();
      int i = 0;
      for (PsiMethod method : entry.getKey().getMethods()) {
        weights.putInt(method, i++);
      }
      ContainerUtil.sort(entry.getValue(), comparator);
      for (Pair<PsiMethod, PsiMethod> pair : entry.getValue()) {
        JavaElementArrangementEntry overridingMethodEntry = myMethodEntriesMap.get(pair.second);
        if (overridingMethodEntry != null) {
          info.addMethodEntry(overridingMethodEntry);
        }
      }
      if (!info.getMethodEntries().isEmpty()) {
        result.add(info);
      }
    }

    return result;
  }

  /**
   * Is expected to be called when new method dependency is detected. Here given <code>'base method'</code> calls
   * <code>'dependent method'</code>.
   */
  public void registerMethodCallDependency(@Nonnull PsiMethod caller, @Nonnull PsiMethod callee) {
    myTmpMethodDependencyRoots.remove(callee);
    if (!myDependentMethods.contains(caller)) {
      myTmpMethodDependencyRoots.add(caller);
    }
    myDependentMethods.add(callee);
    Set<PsiMethod> methods = myMethodDependencies.get(caller);
    if (methods == null) {
      myMethodDependencies.put(caller, methods = new LinkedHashSet<PsiMethod>());
    }
    if (!methods.contains(callee)) {
      methods.add(callee);
    }
    myRebuildMethodDependencies = true;
  }

  public void registerFieldInitializationDependency(@Nonnull PsiField fieldToInitialize, @Nonnull PsiField usedInInitialization) {
    myFieldDependenciesManager.registerInitializationDependency(fieldToInitialize, usedInInitialization);
  }

  @Nonnull
  public List<ArrangementEntryDependencyInfo> getFieldDependencyRoots() {
    return myFieldDependenciesManager.getRoots();
  }

  private static class FieldDependenciesManager {
    private final Map<PsiField, Set<PsiField>> myFieldDependencies = new HashMap<>();
    private final Map<PsiField, ArrangementEntryDependencyInfo> myFieldInfosMap = new HashMap<>();


    public void registerFieldAndEntry(@Nonnull PsiField field, @Nonnull JavaElementArrangementEntry entry) {
      myFieldInfosMap.put(field, new ArrangementEntryDependencyInfo(entry));
    }

    public void registerInitializationDependency(@Nonnull PsiField fieldToInitialize, @Nonnull PsiField usedInInitialization) {
      Set<PsiField> fields = myFieldDependencies.get(fieldToInitialize);
      if (fields == null) {
        fields = new HashSet<PsiField>();
        myFieldDependencies.put(fieldToInitialize, fields);
      }
      fields.add(usedInInitialization);
    }

    @Nonnull
    public List<ArrangementEntryDependencyInfo> getRoots() {
      List<ArrangementEntryDependencyInfo> list = ContainerUtil.newArrayList();

      for (Map.Entry<PsiField, Set<PsiField>> entry : myFieldDependencies.entrySet()) {
        ArrangementEntryDependencyInfo currentInfo = myFieldInfosMap.get(entry.getKey());

        for (PsiField usedInInitialization : entry.getValue()) {
          ArrangementEntryDependencyInfo fieldInfo = myFieldInfosMap.get(usedInInitialization);
          if (fieldInfo != null) {
            currentInfo.addDependentEntryInfo(fieldInfo);
          }
        }

        list.add(currentInfo);
      }

      return list;
    }
  }
}