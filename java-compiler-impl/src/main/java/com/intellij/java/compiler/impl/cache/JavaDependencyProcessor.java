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

/**
 * created at Feb 19, 2002
 *
 * @author Jeka
 */
package com.intellij.java.compiler.impl.cache;

import com.intellij.java.compiler.impl.classParsing.*;
import com.intellij.java.compiler.impl.util.cls.ClsUtil;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.compiler.CacheCorruptedException;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;
import consulo.util.collection.primitive.ints.IntSet;
import consulo.util.collection.primitive.ints.IntSets;
import org.jetbrains.annotations.NonNls;

import java.util.*;

class JavaDependencyProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.JavaDependencyProcessor");
  private final JavaDependencyCache myJavaDependencyCache;
  private final int myQName;
  private final Map<Dependency.MethodRef, MethodInfo> myRefToMethodMap = new HashMap<Dependency.MethodRef, MethodInfo>();
  private final Map<Dependency.FieldRef, FieldInfo> myRefToFieldMap = new HashMap<Dependency.FieldRef, FieldInfo>();
  private final Set<MemberInfo> myAddedMembers = new HashSet<MemberInfo>();
  private final Set<MemberInfo> myRemovedMembers = new HashSet<MemberInfo>();
  private final Set<MemberInfo> myChangedMembers = new HashSet<MemberInfo>();
  private final Map<MemberInfo, ChangeDescription> myChangeDescriptions = new HashMap<MemberInfo, ChangeDescription>();
  private Dependency[] myBackDependencies;
  private final boolean myMembersChanged;
  private final boolean mySuperInterfaceAdded;
  private final boolean mySuperInterfaceRemoved;
  private final boolean mySuperClassChanged;
  private final boolean mySuperlistGenericSignatureChanged;
  private final boolean mySuperClassAdded;
  private final Project myProject;
  private final boolean myIsAnnotation;
  private final boolean myIsRemoteInterface;
  private final boolean myWereAnnotationTargetsRemoved;
  private final boolean myRetentionPolicyChanged;
  private final boolean myAnnotationSemanticsChanged;

  public JavaDependencyProcessor(Project project, JavaDependencyCache javaDependencyCache, int qName) throws CacheCorruptedException {
    myProject = project;
    myJavaDependencyCache = javaDependencyCache;
    myQName = qName;
    final Cache cache = javaDependencyCache.getCache();
    final Cache newClassesCache = javaDependencyCache.getNewClassesCache();

    final MethodInfo[] oldMethods = cache.getMethods(qName);
    for (MethodInfo method : oldMethods) {
      myRefToMethodMap.put(new Dependency.MethodRef(method.getName(), method.getDescriptor()), method);
    }
    final IntObjectMap<FieldInfo> oldFieldsMap = getFieldInfos(cache, qName);
    oldFieldsMap.forEach((fieldName, fieldInfo) -> myRefToFieldMap.put(new Dependency.FieldRef(fieldName), fieldInfo));
    final Map<String, MethodInfoContainer> oldMethodsMap = getMethodInfos(oldMethods);
    final Map<String, MethodInfoContainer> newMethodsMap = getMethodInfos(newClassesCache.getMethods(qName));
    final IntObjectMap<FieldInfo> newFieldsMap = getFieldInfos(newClassesCache, qName);
    addAddedMembers(oldFieldsMap, oldMethodsMap, newFieldsMap, newMethodsMap, myAddedMembers);
    addRemovedMembers(oldFieldsMap, oldMethodsMap, newFieldsMap, newMethodsMap, myRemovedMembers);
    addChangedMembers(oldFieldsMap, oldMethodsMap, newFieldsMap, newMethodsMap, myChangedMembers);

    myMembersChanged = !myAddedMembers.isEmpty() || !myRemovedMembers.isEmpty() || !myChangedMembers.isEmpty();
    // track changes in super list

    myIsRemoteInterface = JavaMakeUtil.isInterface(cache.getFlags(myQName)) && cache.isRemote(qName);
    myIsAnnotation = ClsUtil.isAnnotation(cache.getFlags(qName));
    myWereAnnotationTargetsRemoved = myIsAnnotation && wereAnnotationTargesRemoved(cache, newClassesCache);
    myRetentionPolicyChanged = myIsAnnotation && hasRetentionPolicyChanged(cache, newClassesCache);
    myAnnotationSemanticsChanged = myIsAnnotation && hasAnnotationSemanticsChanged(cache, newClassesCache);

    int[] oldInterfaces = cache.getSuperInterfaces(qName);
    int[] newInterfaces = newClassesCache.getSuperInterfaces(qName);
    mySuperInterfaceRemoved = wereInterfacesRemoved(oldInterfaces, newInterfaces);
    mySuperInterfaceAdded = wereInterfacesRemoved(newInterfaces, oldInterfaces);

    mySuperlistGenericSignatureChanged = isSuperlistGenericSignatureChanged(cache.getGenericSignature(qName),
        newClassesCache.getGenericSignature(qName));

    boolean superclassesDiffer = cache.getSuperQualifiedName(qName) != newClassesCache.getSuperQualifiedName(qName);
    boolean wasDerivedFromObject = CommonClassNames.JAVA_LANG_OBJECT.equals(javaDependencyCache.resolve(cache.getSuperQualifiedName(qName)));
    mySuperClassChanged = !wasDerivedFromObject && superclassesDiffer;
    mySuperClassAdded = wasDerivedFromObject && superclassesDiffer;
  }

  private static boolean hasMembersWithoutDefaults(Set<MemberInfo> addedMembers) {
    for (final Object addedMember : addedMembers) {
      MemberInfo memberInfo = (MemberInfo) addedMember;
      if (memberInfo instanceof MethodInfo) {
        final ConstantValue annotationDefault = ((MethodInfo) memberInfo).getAnnotationDefault();
        if (ConstantValue.EMPTY_CONSTANT_VALUE.equals(annotationDefault)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean wereAnnotationDefaultsRemoved() {
    for (final MemberInfo memberInfo : myChangeDescriptions.keySet()) {
      if (memberInfo instanceof MethodInfo) {
        MethodChangeDescription description = (MethodChangeDescription) myChangeDescriptions.get(memberInfo);
        if (description.removedAnnotationDefault) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isSuperlistGenericSignatureChanged(int oldGenericSignature, int newGenericSignature) throws CacheCorruptedException {
    if (oldGenericSignature == newGenericSignature) {
      return false;
    }
    if (oldGenericSignature != -1 && newGenericSignature != -1) {
      final SymbolTable symbolTable = myJavaDependencyCache.getSymbolTable();
      final String _oldGenericMethodSignature = cutFormalParams(symbolTable.getSymbol(oldGenericSignature));
      final String _newGenericMethodSignature = cutFormalParams(symbolTable.getSymbol(newGenericSignature));
      return !_oldGenericMethodSignature.equals(_newGenericMethodSignature);
    }
    return true;
  }

  private static String cutFormalParams(String genericClassSignature) {
    if (genericClassSignature.charAt(0) == '<') {
      int idx = genericClassSignature.indexOf('>');
      return genericClassSignature.substring(idx + 1);
    }
    return genericClassSignature;
  }

  public void run() throws CacheCorruptedException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Checking dependencies for " + myJavaDependencyCache.resolve(myQName));
    }
    final boolean superListChanged = mySuperClassChanged || mySuperClassAdded || mySuperInterfaceAdded || mySuperInterfaceRemoved || mySuperlistGenericSignatureChanged;
    final Cache oldCache = myJavaDependencyCache.getCache();
    final Cache newCache = myJavaDependencyCache.getNewClassesCache();

    if (!myMembersChanged &&
        oldCache.getFlags(myQName) == newCache.getFlags(myQName) &&
        !superListChanged && !myWereAnnotationTargetsRemoved && !myRetentionPolicyChanged && !myAnnotationSemanticsChanged) {
      return; // nothing to do
    }

    if (myIsAnnotation) {
      if (myAnnotationSemanticsChanged) {
        final IntSet visited = IntSets.newHashSet();
        visited.add(myQName);
        markAnnotationDependenciesRecursively(getBackDependencies(), LOG.isDebugEnabled() ? "; reason: semantics changed for " + myJavaDependencyCache.resolve(myQName) : "", visited);
        return;
      }
      if (hasMembersWithoutDefaults(myAddedMembers)) {
        markAll(getBackDependencies(), LOG.isDebugEnabled() ? "; reason: added annotation type member without default " + myJavaDependencyCache.resolve(myQName) : "");
        return;
      }
      if (!myRemovedMembers.isEmpty()) {
        markAll(getBackDependencies(), LOG.isDebugEnabled() ? "; reason: removed annotation type member " + myJavaDependencyCache.resolve(myQName) : "");
        return;
      }
      if (!myChangedMembers.isEmpty()) { // for annotations "changed" means return type changed
        markAll(getBackDependencies(), LOG.isDebugEnabled() ? "; reason: changed annotation member's type " + myJavaDependencyCache.resolve(myQName) : "");
        return;
      }
      if (wereAnnotationDefaultsRemoved()) {
        markAll(getBackDependencies(), LOG.isDebugEnabled() ? "; reason: removed annotation member's default value " + myJavaDependencyCache.resolve(myQName) : "");
        return;
      }
      if (myWereAnnotationTargetsRemoved) {
        markAll(getBackDependencies(), LOG.isDebugEnabled() ? "; reason: removed annotation's targets " + myJavaDependencyCache.resolve(myQName) : "");
        return;
      }
      if (myRetentionPolicyChanged) {
        markAll(getBackDependencies(), LOG.isDebugEnabled() ? "; reason: retention policy changed for " + myJavaDependencyCache.resolve(myQName) : "");
        return;
      }
    }

    final JavaDependencyCacheNavigator cacheNavigator = myJavaDependencyCache.getCacheNavigator();

    if (mySuperClassChanged || mySuperInterfaceRemoved || mySuperlistGenericSignatureChanged) {
      // superclass changed == old removed and possibly new added
      // if anything (class or interface) in the superlist was removed, should recompile all subclasses (both direct and indirect)
      // and all back-dependencies of this class and its subclasses
      markAll(getBackDependencies(), LOG.isDebugEnabled() ? "; reason: deleted items from the superlist or changed superlist generic signature of " + myJavaDependencyCache.resolve(myQName) :
          "");
      cacheNavigator.walkSubClasses(myQName, new ClassInfoProcessor() {
        public boolean process(int classQName) throws CacheCorruptedException {
          markAll(oldCache.getBackDependencies(classQName), LOG.isDebugEnabled() ? "; reason: deleted items from the superlist or changed superlist generic signature of " +
              myJavaDependencyCache.resolve(myQName) : "");
          return true;
        }
      });
      return;
    }

    final boolean isKindChanged =
        (JavaMakeUtil.isInterface(oldCache.getFlags(myQName)) && !JavaMakeUtil.isInterface(newCache.getFlags(myQName))) ||
            (!JavaMakeUtil.isInterface(oldCache.getFlags(myQName)) && JavaMakeUtil.isInterface(newCache.getFlags(myQName)));
    if (isKindChanged) {
      markAll(getBackDependencies(), LOG.isDebugEnabled() ? "; reason: class kind changed (class/interface) " + myJavaDependencyCache.resolve(myQName) : "");
      cacheNavigator.walkSubClasses(myQName, new ClassInfoProcessor() {
        public boolean process(int classQName) throws CacheCorruptedException {
          markAll(oldCache.getBackDependencies(classQName), LOG.isDebugEnabled() ? "; reason: class kind changed (class/interface) " + myJavaDependencyCache.resolve(myQName) : "");
          return true;
        }
      });
      return;
    }

    boolean becameFinal = !ClsUtil.isFinal(oldCache.getFlags(myQName)) && ClsUtil.isFinal(newCache.getFlags(myQName));
    if (becameFinal) {
      markAll(getBackDependencies(), LOG.isDebugEnabled() ? "; reason: class became final: " + myJavaDependencyCache.resolve(myQName) : "");
    } else {
      boolean becameAbstract = !ClsUtil.isAbstract(oldCache.getFlags(myQName)) && ClsUtil.isAbstract(newCache.getFlags(myQName));
      boolean accessRestricted = JavaMakeUtil.isMoreAccessible(oldCache.getFlags(myQName), newCache.getFlags(myQName));
      Set<MethodInfo> removedMethods = null;
      Set<MethodInfo> addedMethods = null;
      for (Dependency backDependency : getBackDependencies()) {
        if (myJavaDependencyCache.isTargetClassInfoMarked(backDependency)) {
          continue;
        }

        if (accessRestricted) {
          if (myJavaDependencyCache.markTargetClassInfo(backDependency)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(backDependency.getClassQualifiedName()) + "; reason: " +
                  myJavaDependencyCache.resolve(myQName) + " made less accessible");
            }
          }
          continue;
        }
        if (becameAbstract) {
          if (processClassBecameAbstract(backDependency)) {
            continue;
          }
        }
        if (isDependentOnRemovedMembers(backDependency)) {
          if (myJavaDependencyCache.markTargetClassInfo(backDependency)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(backDependency.getClassQualifiedName()) +
                  "; reason: the class uses removed members of " + myJavaDependencyCache.resolve(myQName));
            }
          }
          continue;
        }
        if (isDependentOnChangedMembers(backDependency)) {
          if (myJavaDependencyCache.markTargetClassInfo(backDependency)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(backDependency.getClassQualifiedName()) +
                  "; reason: the class uses changed members of " + myJavaDependencyCache.resolve(myQName));
            }
          }
          continue;
        }
        final Collection<Dependency.MethodRef> usedMethods = backDependency.getMethodRefs();
        if (removedMethods == null) {
          removedMethods = extractMethods(myRemovedMembers, true);
        }
        if (isDependentOnEquivalentMethods(usedMethods, removedMethods)) {
          if (myJavaDependencyCache.markTargetClassInfo(backDependency)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(backDependency.getClassQualifiedName()) +
                  "; reason: some overloaded methods of " + myJavaDependencyCache.resolve(myQName) + " were removed");
            }
          }
          continue;
        }
        if (addedMethods == null) {
          addedMethods = extractMethods(myAddedMembers, true);
        }
        if (isDependentOnEquivalentMethods(usedMethods, addedMethods)) {
          if (myJavaDependencyCache.markTargetClassInfo(backDependency)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(backDependency.getClassQualifiedName()) +
                  "; reason: some overloaded methods of " + myJavaDependencyCache.resolve(myQName) + " were added");
            }
          }
        }
      }
    }

    final Set<MethodInfo> methodsToCheck = new HashSet<MethodInfo>();
    extractMethods(myRemovedMembers, methodsToCheck, false);

    processInheritanceDependencies(methodsToCheck);

    extractMethods(myAddedMembers, methodsToCheck, false);

    if (!JavaMakeUtil.isAnonymous(myJavaDependencyCache.resolve(myQName))) {
      // these checks make no sense for anonymous classes

      final IntSet fieldNames = IntSets.newHashSet();
      extractFieldNames(myAddedMembers, fieldNames);
      int addedFieldsCount = fieldNames.size();
      extractFieldNames(myRemovedMembers, fieldNames);

      if (!fieldNames.isEmpty()) {
        cacheNavigator.walkSuperClasses(myQName, new ClassInfoProcessor() {
          public boolean process(final int classQName) throws CacheCorruptedException {
            markUseDependenciesOnFields(classQName, fieldNames);
            return true;
          }
        });
      }

      if (addedFieldsCount > 0 && JavaMakeUtil.isInterface(oldCache.getFlags(myQName))) {
        final IntSet visitedClasses = IntSets.newHashSet();
        visitedClasses.add(myQName);
        cacheNavigator.walkSubClasses(myQName, new ClassInfoProcessor() {
          public boolean process(int subclassQName) throws CacheCorruptedException {
            markUseDependenciesOnFields(subclassQName, fieldNames);
            visitedClasses.add(subclassQName);
            cacheNavigator.walkSuperClasses(subclassQName, new ClassInfoProcessor() {
              public boolean process(int superclassQName) throws CacheCorruptedException {
                if (visitedClasses.contains(superclassQName)) {
                  return false;
                }
                markUseDependenciesOnFields(superclassQName, fieldNames);
                visitedClasses.add(superclassQName);
                return true;
              }
            });
            return true;
          }
        });
      }

      if (!methodsToCheck.isEmpty()) {
        cacheNavigator.walkSuperClasses(myQName, new ClassInfoProcessor() {
          public boolean process(int classQName) throws CacheCorruptedException {
            markUseDependenciesOnEquivalentMethods(classQName, methodsToCheck, myQName);
            return true;
          }
        });

        cacheNavigator.walkSubClasses(myQName, new ClassInfoProcessor() {
          public boolean process(int classQName) throws CacheCorruptedException {
            markUseDependenciesOnEquivalentMethods(classQName, methodsToCheck, myQName);
            return true;
          }
        });
      }
      // check referencing members in subclasses

      final IntSet addedOrRemovedFields = IntSets.newHashSet();
      final IntSet addedOrRemovedMethods = IntSets.newHashSet();
      for (Set<MemberInfo> infos : Arrays.asList(myAddedMembers, myRemovedMembers)) {
        for (MemberInfo member : infos) {
          if (!member.isPrivate()) {
            if (member instanceof FieldInfo) {
              addedOrRemovedFields.add(member.getName());
            } else if (member instanceof MethodInfo) {
              addedOrRemovedMethods.add(member.getName());
            }
          }
        }

      }
      if (!addedOrRemovedFields.isEmpty() || !addedOrRemovedMethods.isEmpty()) {
        cacheNavigator.walkSubClasses(myQName, new ClassInfoProcessor() {
          public boolean process(final int subclassQName) throws CacheCorruptedException {
            if (!myJavaDependencyCache.isClassInfoMarked(subclassQName)) {
              if (referencesMembersWithNames(oldCache, subclassQName, addedOrRemovedFields, addedOrRemovedMethods)) {
                final boolean marked = myJavaDependencyCache.markClass(subclassQName);
                if (marked && LOG.isDebugEnabled()) {
                  LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; Reason: members were added/removed in superclass with names, that may clash" +
                      " " +
                      "with the names of members of another classes that this class references");
                }
              }
            }
            return true;
          }
        });
      }
    }
  }

  private static boolean referencesMembersWithNames(Cache cache, final int qName, IntSet fieldNames, IntSet methodNames) throws CacheCorruptedException {
    for (final int referencedClass : cache.getReferencedClasses(qName)) {
      for (Dependency dependency : cache.getBackDependencies(referencedClass)) {
        if (dependency.getClassQualifiedName() == qName) {
          for (Dependency.FieldRef ref : dependency.getFieldRefs()) {
            if (fieldNames.contains(ref.name)) {
              return true;
            }
          }
          for (Dependency.MethodRef ref : dependency.getMethodRefs()) {
            if (methodNames.contains(ref.name)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private void markAnnotationDependenciesRecursively(final Dependency[] dependencies, final @NonNls String reason, final IntSet visitedAnnotations)
      throws CacheCorruptedException {
    final Cache oldCache = myJavaDependencyCache.getCache();
    for (Dependency dependency : dependencies) {
      if (myJavaDependencyCache.markTargetClassInfo(dependency)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(dependency.getClassQualifiedName()) + reason);
        }
      }
      final int depQName = dependency.getClassQualifiedName();
      if (ClsUtil.isAnnotation(oldCache.getFlags(depQName))) {
        if (!visitedAnnotations.contains(depQName)) {
          visitedAnnotations.add(depQName);
          markAnnotationDependenciesRecursively(oldCache.getBackDependencies(depQName), LOG.isDebugEnabled() ? "; reason: cascade semantics change for " + myJavaDependencyCache.resolve
              (depQName) : "", visitedAnnotations);
        }
      }
    }
  }

  private static final int[] ALL_TARGETS = {
      AnnotationTargets.ANNOTATION_TYPE,
      AnnotationTargets.CONSTRUCTOR,
      AnnotationTargets.FIELD,
      AnnotationTargets.LOCAL_VARIABLE,
      AnnotationTargets.METHOD,
      AnnotationTargets.PACKAGE,
      AnnotationTargets.PARAMETER,
      AnnotationTargets.TYPE
  };

  private boolean wereAnnotationTargesRemoved(final Cache oldCache, final Cache newCache) throws CacheCorruptedException {
    final int oldAnnotationTargets = JavaMakeUtil.getAnnotationTargets(oldCache, myQName, myJavaDependencyCache.getSymbolTable());
    final int newAnnotationTargets = JavaMakeUtil.getAnnotationTargets(newCache, myQName, myJavaDependencyCache.getSymbolTable());
    if (oldAnnotationTargets == newAnnotationTargets) {
      return false;
    }
    for (final int target : ALL_TARGETS) {
      if ((oldAnnotationTargets & target) != 0 && (newAnnotationTargets & target) == 0) {
        return true;
      }
    }
    return false;
  }

  private boolean hasRetentionPolicyChanged(final Cache oldCache, final Cache newCache) throws CacheCorruptedException {
    // if retention policy changed from SOURCE to CLASS or RUNTIME, all sources should be recompiled to propagate changes
    final int oldPolicy = JavaMakeUtil.getAnnotationRetentionPolicy(myQName, oldCache, myJavaDependencyCache.getSymbolTable());
    final int newPolicy = JavaMakeUtil.getAnnotationRetentionPolicy(myQName, newCache, myJavaDependencyCache.getSymbolTable());
    if (oldPolicy == RetentionPolicies.SOURCE && (newPolicy == RetentionPolicies.CLASS || newPolicy == RetentionPolicies.RUNTIME)) {
      return true;
    }
    return oldPolicy == RetentionPolicies.CLASS && newPolicy == RetentionPolicies.RUNTIME;
  }

  private boolean hasAnnotationSemanticsChanged(final Cache oldCache, final Cache newCache) throws CacheCorruptedException {
    final IntObjectMap<AnnotationConstantValue> oldAnnotations = fetchAllAnnotations(oldCache);
    final IntObjectMap<AnnotationConstantValue> newAnnotations = fetchAllAnnotations(newCache);
    // filter certain known annotation which are processed separately
    final int retentionAnnotation = myJavaDependencyCache.getSymbolTable().getId(CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION);
    final int targetAnnotation = myJavaDependencyCache.getSymbolTable().getId(CommonClassNames.JAVA_LANG_ANNOTATION_TARGET);
    oldAnnotations.remove(retentionAnnotation);
    oldAnnotations.remove(targetAnnotation);
    newAnnotations.remove(retentionAnnotation);
    newAnnotations.remove(targetAnnotation);

    if (oldAnnotations.size() != newAnnotations.size()) {
      return true; // number of annotation has changed
    }
    for (int annotName : oldAnnotations.keys()) {
      if (!newAnnotations.containsKey(annotName)) {
        return true;
      }
      final AnnotationNameValuePair[] oldValues = oldAnnotations.get(annotName).getMemberValues();
      final AnnotationNameValuePair[] newValues = newAnnotations.get(annotName).getMemberValues();
      if (annotationValuesDiffer(oldValues, newValues)) {
        return true;
      }
    }
    return false;
  }

  private boolean annotationValuesDiffer(final AnnotationNameValuePair[] oldValues, final AnnotationNameValuePair[] newValues) {
    if (oldValues.length != newValues.length) {
      return true;
    }
    final IntObjectMap<ConstantValue> names = IntMaps.newIntObjectHashMap();
    for (AnnotationNameValuePair value : oldValues) {
      names.put(value.getName(), value.getValue());
    }
    for (AnnotationNameValuePair value : newValues) {
      if (!names.containsKey(value.getName())) {
        return true;
      }
      if (!value.getValue().equals(names.get(value.getName()))) {
        return true;
      }
    }
    return false;
  }


  private IntObjectMap<AnnotationConstantValue> fetchAllAnnotations(final Cache cache) throws CacheCorruptedException {
    final int classId = myQName;
    IntObjectMap<AnnotationConstantValue> oldAnnotations = IntMaps.newIntObjectHashMap();
    for (AnnotationConstantValue annot : cache.getRuntimeVisibleAnnotations(classId)) {
      oldAnnotations.put(annot.getAnnotationQName(), annot);
    }
    for (AnnotationConstantValue annot : cache.getRuntimeInvisibleAnnotations(classId)) {
      oldAnnotations.put(annot.getAnnotationQName(), annot);
    }
    return oldAnnotations;
  }

  private void markAll(Dependency[] backDependencies, @NonNls String reason) throws CacheCorruptedException {
    for (Dependency backDependency : backDependencies) {
      if (myJavaDependencyCache.markTargetClassInfo(backDependency)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(backDependency.getClassQualifiedName()) + reason);
        }
      }
    }
  }

  private static void extractFieldNames(Collection<MemberInfo> fromCollection, IntSet toCollection) {
    for (final Object aFromCollection : fromCollection) {
      MemberInfo memberInfo = (MemberInfo) aFromCollection;
      if (memberInfo instanceof FieldInfo) {
        toCollection.add(memberInfo.getName());
      }
    }
  }

  private static Set<MethodInfo> extractMethods(Collection<MemberInfo> fromCollection, boolean includeConstructors) {
    final Set<MethodInfo> methods = new HashSet<MethodInfo>();
    extractMethods(fromCollection, methods, includeConstructors);
    return methods;
  }

  private static void extractMethods(Collection<MemberInfo> fromCollection, Collection<MethodInfo> toCollection, boolean includeConstructors) {
    for (final MemberInfo memberInfo : fromCollection) {
      if (memberInfo instanceof MethodInfo) {
        final MethodInfo methodInfo = (MethodInfo) memberInfo;
        if (includeConstructors) {
          toCollection.add(methodInfo);
        } else {
          if (!methodInfo.isConstructor()) {
            toCollection.add(methodInfo);
          }
        }
      }
    }
  }

  private boolean processClassBecameAbstract(Dependency dependency) throws CacheCorruptedException {
    for (Dependency.MethodRef ref : dependency.getMethodRefs()) {
      final MethodInfo usedMethod = myRefToMethodMap.get(ref);
      if (usedMethod == null) {
        continue;
      }
      if (usedMethod.isConstructor()) {
        if (myJavaDependencyCache.markTargetClassInfo(dependency)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(dependency.getClassQualifiedName()) + "; reason: " +
                myJavaDependencyCache.resolve(myQName) + " made abstract");
          }
        }
        return true;
      }
    }
    return false;
  }

  private boolean isDependentOnRemovedMembers(Dependency dependency) {
    for (Dependency.MethodRef ref : dependency.getMethodRefs()) {
      if (myRemovedMembers.contains(myRefToMethodMap.get(ref))) {
        return true;
      }
    }
    for (Dependency.FieldRef ref : dependency.getFieldRefs()) {
      if (myRemovedMembers.contains(myRefToFieldMap.get(ref))) {
        return true;
      }
    }
    return false;
  }

  private boolean isDependentOnChangedMembers(Dependency dependency) {
    for (Dependency.FieldRef ref : dependency.getFieldRefs()) {
      final FieldInfo fieldInfo = myRefToFieldMap.get(ref);
      if (myChangedMembers.contains(fieldInfo)) {
        return true;
      }
    }

    for (Dependency.MethodRef ref : dependency.getMethodRefs()) {
      final MethodInfo methodInfo = myRefToMethodMap.get(ref);
      if (myChangedMembers.contains(methodInfo)) {
        final MethodChangeDescription changeDescription = (MethodChangeDescription) myChangeDescriptions.get(methodInfo);
        if (changeDescription.returnTypeDescriptorChanged ||
            changeDescription.returnTypeGenericSignatureChanged ||
            changeDescription.paramsGenericSignatureChanged ||
            changeDescription.throwsListChanged ||
            changeDescription.staticPropertyChanged ||
            changeDescription.accessRestricted) {
          return true;
        }
      }
    }

    return false;
  }

  private boolean isDependentOnEquivalentMethods(Collection<Dependency.MethodRef> checkedMembers, Set<MethodInfo> members) throws CacheCorruptedException {
    // check if 'members' contains method with the same name and the same numbers of parameters, but with different types
    if (checkedMembers.isEmpty() || members.isEmpty()) {
      return false; // optimization
    }
    for (Dependency.MethodRef checkedMethod : checkedMembers) {
      if (hasEquivalentMethod(members, checkedMethod)) {
        return true;
      }
    }
    return false;
  }

  private void markUseDependenciesOnEquivalentMethods(final int checkedInfoQName, Set<MethodInfo> methodsToCheck, int methodsClassName) throws CacheCorruptedException {
    final Dependency[] backDependencies = myJavaDependencyCache.getCache().getBackDependencies(checkedInfoQName);
    for (Dependency dependency : backDependencies) {
      if (myJavaDependencyCache.isTargetClassInfoMarked(dependency)) {
        continue;
      }
      if (isDependentOnEquivalentMethods(dependency.getMethodRefs(), methodsToCheck)) {
        if (myJavaDependencyCache.markTargetClassInfo(dependency)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(dependency.getClassQualifiedName()) +
                "; reason: more specific methods added to " + myJavaDependencyCache.resolve(methodsClassName));
          }
        }
        myJavaDependencyCache.addClassToUpdate(checkedInfoQName);
      }
    }
  }

  private void markUseDependenciesOnFields(final int classQName, IntSet fieldNames) throws CacheCorruptedException {
    final Cache oldCache = myJavaDependencyCache.getCache();
    for (Dependency useDependency : oldCache.getBackDependencies(classQName)) {
      if (!myJavaDependencyCache.isTargetClassInfoMarked(useDependency)) {
        for (Dependency.FieldRef field : useDependency.getFieldRefs()) {
          if (fieldNames.contains(field.name)) {
            if (myJavaDependencyCache.markTargetClassInfo(useDependency)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(useDependency.getClassQualifiedName()) +
                    "; reason: conflicting fields were added to the hierarchy of the class " + myJavaDependencyCache.resolve(classQName));
              }
            }
            myJavaDependencyCache.addClassToUpdate(classQName);
            break; // stop iterating fields
          }
        }
      }
    }
  }

  private void processInheritanceDependencies(final Set<MethodInfo> removedMethods) throws CacheCorruptedException {
    final Cache oldCache = myJavaDependencyCache.getCache();
    final Cache newCache = myJavaDependencyCache.getNewClassesCache();

    final boolean becameFinal = !ClsUtil.isFinal(oldCache.getFlags(myQName)) && ClsUtil.isFinal(newCache.getFlags(myQName));
    final SymbolTable symbolTable = myJavaDependencyCache.getSymbolTable();

    final Set<MemberInfo> removedConcreteMethods = fetchNonAbstractMethods(myRemovedMembers);
    final Set<MethodInfo> removedOverridableMethods;
    if (!removedMethods.isEmpty()) {
      removedOverridableMethods = new HashSet<MethodInfo>(removedMethods);
      for (Iterator<MethodInfo> it = removedOverridableMethods.iterator(); it.hasNext(); ) {
        final MethodInfo method = it.next();
        if (method.isFinal() || method.isStatic() || method.isPrivate() || method.isConstructor()) {
          it.remove();
        }
      }
    } else {
      removedOverridableMethods = Collections.emptySet();
    }
    myJavaDependencyCache.getCacheNavigator().walkSubClasses(myQName, new ClassInfoProcessor() {
      public boolean process(final int subclassQName) throws CacheCorruptedException {
        if (myJavaDependencyCache.isClassInfoMarked(subclassQName)) {
          return true;
        }

        if (!oldCache.containsClass(subclassQName)) {
          return true;
        }

        if (!removedMethods.isEmpty() && myIsRemoteInterface && !JavaMakeUtil.isInterface(oldCache.getFlags(subclassQName))) {
          if (myJavaDependencyCache.markClass(subclassQName)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) +
                  "; reason: methods were removed from remote interface: " + myJavaDependencyCache.resolve(myQName));
            }
          }
          return true;
        }

        if (mySuperClassAdded || mySuperInterfaceAdded) {
          if (myJavaDependencyCache.markClass(subclassQName)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: the superlist of " +
                  myJavaDependencyCache.resolve(myQName) + " is changed");
            }
          }
          return true;
        }

        // if info became final, mark direct inheritors
        if (becameFinal) {
          if (myQName == oldCache.getSuperQualifiedName(subclassQName)) {
            if (myJavaDependencyCache.markClass(subclassQName)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: the class " +
                    myJavaDependencyCache.resolve(myQName) + " was made final");
              }
            }
            return true;
          }
        }

        // process added members
        for (final MemberInfo member : myAddedMembers) {
          if (member instanceof MethodInfo) {
            final MethodInfo method = (MethodInfo) member;
            if (method.isAbstract()) {
              // all derived classes should be marked in case an abstract method was added
              if (myJavaDependencyCache.markClass(subclassQName)) {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: added abstract method to " +
                      myJavaDependencyCache.resolve(myQName));
                }
              }
              return true;
            }
            if (!method.isPrivate()) {
              final MethodInfo derivedMethod = oldCache.findMethodsBySignature(subclassQName, method.getDescriptor(symbolTable), symbolTable);
              if (derivedMethod != null) {
                if (!method.getReturnTypeDescriptor(symbolTable).equals(derivedMethod.getReturnTypeDescriptor(symbolTable))) {
                  if (myJavaDependencyCache.markClass(subclassQName)) {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: return types of method " +
                          method + " in base and derived classes are different");
                    }
                  }
                  return true;
                }
                if (JavaMakeUtil.isMoreAccessible(method.getFlags(), derivedMethod.getFlags())) {
                  if (myJavaDependencyCache.markClass(subclassQName)) {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: the method " + method +
                          " in derived class is less accessible than in base class");
                    }
                  }
                  return true;
                }
                if (!method.isStatic() && derivedMethod.isStatic()) {
                  if (myJavaDependencyCache.markClass(subclassQName)) {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: the method " + method +
                          " in derived class is static, but added method in the base class is not");
                    }
                  }
                  return true;
                }
                if (method.isFinal() && !derivedMethod.isFinal()) {
                  if (myJavaDependencyCache.markClass(subclassQName)) {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: the method " + method +
                          " in base class is final, but in derived class is not");
                    }
                  }
                  return true;
                }
                if (!JavaCacheUtils.areArraysContentsEqual(method.getThrownExceptions(), derivedMethod.getThrownExceptions())) {
                  if (myJavaDependencyCache.markClass(subclassQName)) {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: exception lists of " +
                          method + " in base and derived classes are different");
                    }
                  }
                  return true;
                }
              }
              if (hasGenericsNameClashes(method, oldCache, subclassQName)) {
                if (myJavaDependencyCache.markClass(subclassQName)) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) +
                        "; reason: found method with the same name, different generic signature, but the same erasure as " + method);
                  }
                }
                return true;
              }
            }
          } else if (member instanceof FieldInfo) {
            if (oldCache.findFieldByName(subclassQName, member.getName()) != null) {
              if (myJavaDependencyCache.markClass(subclassQName)) {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: added field " + member +
                      " to base class");
                }
              }
              return true;
            }
          }
        }

        // process changed members
        for (final MemberInfo changedMember : myChangedMembers) {
          if (changedMember instanceof MethodInfo) {
            final MethodInfo oldMethod = (MethodInfo) changedMember;
            MethodChangeDescription changeDescription = (MethodChangeDescription) myChangeDescriptions.get(oldMethod);
            if (changeDescription.becameAbstract) {
              if (!ClsUtil.isAbstract(oldCache.getFlags(subclassQName))) { // if the subclass was not abstract
                if (myJavaDependencyCache.markClass(subclassQName)) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: changed base method " + oldMethod);
                  }
                }
                return true;
              }
            }

            final String oldMethodDescriptor = oldMethod.getDescriptor(symbolTable);

            final MethodInfo derivedMethod = oldCache.findMethodsBySignature(subclassQName, oldMethodDescriptor, symbolTable);
            if (derivedMethod != null) {
              if (myJavaDependencyCache.markClass(subclassQName)) {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: changed base method " + oldMethod);
                }
              }
              return true;
            }
            // now check if the changed method is compatible with methods declared in implemented interfaces of subclasses
            myJavaDependencyCache.getCacheNavigator().walkSuperInterfaces(subclassQName, new ClassInfoProcessor() {
              boolean found = false;

              public boolean process(final int ifaceQName) throws CacheCorruptedException {
                if (found) {
                  return false;
                }
                final MethodInfo implementee = oldCache.findMethodsBySignature(ifaceQName, oldMethodDescriptor, symbolTable);
                if (implementee != null) {
                  found = true;
                  if (myJavaDependencyCache.markClass(subclassQName)) {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: changed base method, implementing corresponding method " +
                          "inherited from an interface" + oldMethod);
                    }
                  }
                }
                return !found;
              }
            });
            if (myJavaDependencyCache.isClassInfoMarked(subclassQName)) {
              return true;
            }
          }
        }

        if (!ClsUtil.isAbstract(oldCache.getFlags(subclassQName))) {
          if (hasUnimplementedAbstractMethods(subclassQName, new HashSet<MemberInfo>(removedConcreteMethods))) {
            if (myJavaDependencyCache.markClass(subclassQName)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Mark dependent class " + myJavaDependencyCache.resolve(subclassQName) + "; reason: the class should be declared abstract because abstract method " +
                    "implementation was removed from its superclass: " +
                    myJavaDependencyCache.resolve(myQName));
              }
            }
            return true;
          }
        }

        if (!removedOverridableMethods.isEmpty() && !myJavaDependencyCache.isClassInfoMarked(subclassQName) && !myJavaDependencyCache.getNewClassesCache().containsClass(subclassQName) /*not
        compiled in this session*/) {
          final Cache cache = myJavaDependencyCache.getCache();
          for (MethodInfo subclassMethod : cache.getMethods(subclassQName)) {
            if (!subclassMethod.isConstructor()) {
              for (MethodInfo removedMethod : removedOverridableMethods) {
                if (removedMethod.getName() == subclassMethod.getName() /*todo: check param signatures here for better accuracy*/) {
                  // got it
                  if (myJavaDependencyCache.markClass(subclassQName)) {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Mark dependent subclass " + myJavaDependencyCache.resolve(subclassQName) + "; reason: the class has methods annotated with @Override and some" +
                          " " +
                          "methods were changed or removed in a base class" +
                          myJavaDependencyCache.resolve(myQName));
                    }
                  }
                  return true;
                }
              }
            }
          }
        }
        // end of subclass processor
        return true;
      }
    });
  }

  private static boolean hasGenericsNameClashes(final MethodInfo baseMethod, final Cache oldCache, final int subclassQName) throws CacheCorruptedException {
    // it is illegal if 2 methods in a hierarchy have 1) same name 2) different signatures 3) same erasure
    final List<MethodInfo> methods = oldCache.findMethodsByName(subclassQName, baseMethod.getName());
    if (methods.size() > 0) {
      for (final MethodInfo methodInSubclass : methods) {
        if (ClsUtil.isBridge(methodInSubclass.getFlags())) {
          continue;
        }
        if (baseMethod.getDescriptor() == methodInSubclass.getDescriptor() && baseMethod.getGenericSignature() != methodInSubclass.getGenericSignature()) {
          return true;
        }
      }
    }
    return false;
  }

  private static Set<MemberInfo> fetchNonAbstractMethods(Set<MemberInfo> membersToCheck) {
    final Set<MemberInfo> methodsToCheck = new HashSet<MemberInfo>();
    for (final Object aMembersToCheck : membersToCheck) {
      final MemberInfo memberInfo = (MemberInfo) aMembersToCheck;
      if (memberInfo instanceof MethodInfo) {
        final MethodInfo methodInfo = (MethodInfo) memberInfo;
        if (!methodInfo.isAbstract() && !methodInfo.isConstructor()) {
          methodsToCheck.add(memberInfo);
        }
      }
    }
    return methodsToCheck;
  }

  private boolean hasUnimplementedAbstractMethods(int superQName, final Set methodsToCheck) throws CacheCorruptedException {
    if (myJavaDependencyCache.getCache().containsClass(superQName)) {
      return hasBaseAbstractMethods(superQName, methodsToCheck) ||
          hasBaseAbstractMethodsInHierarchy(superQName, methodsToCheck);
    } else {
      final String qName = myJavaDependencyCache.resolve(superQName);
      if (!CommonClassNames.JAVA_LANG_OBJECT.equals(qName)) {
        if (hasBaseAbstractMethods2(qName, methodsToCheck)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean hasBaseAbstractMethodsInHierarchy(int fromClassQName, final Set methodsToCheck) throws CacheCorruptedException {
    if (fromClassQName == Cache.UNKNOWN || methodsToCheck.isEmpty()) {
      return false;
    }
    final Cache cache = myJavaDependencyCache.getCache();
    int superName = cache.getSuperQualifiedName(fromClassQName);
    if (superName != Cache.UNKNOWN) {
      if (hasUnimplementedAbstractMethods(superName, methodsToCheck)) {
        return true;
      }
    }
    if (methodsToCheck.isEmpty()) {
      return false;
    }
    int[] superInterfaces = cache.getSuperInterfaces(fromClassQName);
    for (int superInterface : superInterfaces) {
      if (hasUnimplementedAbstractMethods(superInterface, methodsToCheck)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasBaseAbstractMethods(int qName, Set methodsToCheck) throws CacheCorruptedException {
    final SymbolTable symbolTable = myJavaDependencyCache.getSymbolTable();
    final Cache oldCache = myJavaDependencyCache.getCache();
    final Cache newCache = myJavaDependencyCache.getNewClassesCache();
    final Cache cache = newCache.containsClass(qName) ? newCache : oldCache; // use recompiled version (if any) for searching methods
    for (Iterator it = methodsToCheck.iterator(); it.hasNext(); ) {
      final MethodInfo methodInfo = (MethodInfo) it.next();
      final MethodInfo superMethod = cache.findMethodsBySignature(qName, methodInfo.getDescriptor(symbolTable), symbolTable);
      if (superMethod != null) {
        if (ClsUtil.isAbstract(superMethod.getFlags())) {
          return true;
        }
        it.remove();
      }
    }
    return false;
  }

  // search using PSI
  private boolean hasBaseAbstractMethods2(final String qName, final Set methodsToCheck) throws CacheCorruptedException {
    final boolean[] found = {false};
    final CacheCorruptedException ex = ApplicationManager.getApplication().runReadAction(new Computable<CacheCorruptedException>() {
      public CacheCorruptedException compute() {
        try {
          final PsiManager psiManager = PsiManager.getInstance(myProject);
          final PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(qName, GlobalSearchScope.allScope(myProject));
          if (aClass == null) {
            return null;
          }
          final PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
          final PsiNameHelper nameHelper = PsiNameHelper.getInstance(myProject);
          for (Iterator it = methodsToCheck.iterator(); it.hasNext(); ) {
            final MethodInfo methodInfo = (MethodInfo) it.next();
            if (!nameHelper.isIdentifier(myJavaDependencyCache.resolve(methodInfo.getName()), LanguageLevel.JDK_1_3)) { // fix for SCR 16068
              continue;
            }
            // language level 1.3 will prevent exceptions from PSI if there are methods named "assert"
            final PsiMethod methodPattern = factory.createMethodFromText(getMethodText(methodInfo), null, LanguageLevel.JDK_1_3);
            final PsiMethod superMethod = aClass.findMethodBySignature(methodPattern, true);
            if (superMethod != null) {
              if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
                found[0] = true;
                return null;
              }
              it.remove();
            }
          }
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        } catch (CacheCorruptedException e) {
          return e;
        }
        return null;
      }
    });
    if (ex != null) {
      throw ex;
    }
    return found[0];
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private
  @NonNls
  String getMethodText(MethodInfo methodInfo) throws CacheCorruptedException {
    final SymbolTable symbolTable = myJavaDependencyCache.getSymbolTable();
    StringBuilder text = new StringBuilder(16);
    final String returnType = signatureToSourceTypeName(methodInfo.getReturnTypeDescriptor(symbolTable));
    text.append(returnType);
    text.append(" ");
    text.append(myJavaDependencyCache.resolve(methodInfo.getName()));
    text.append("(");
    final String[] parameterSignatures = methodInfo.getParameterDescriptors(symbolTable);
    for (int idx = 0; idx < parameterSignatures.length; idx++) {
      String parameterSignature = parameterSignatures[idx];
      if (idx > 0) {
        text.append(",");
      }
      text.append(signatureToSourceTypeName(parameterSignature));
      text.append(" arg");
      text.append(idx);
    }
    text.append(")");
    return text.toString();
  }

  private static boolean wereInterfacesRemoved(int[] oldInterfaces, int[] newInterfaces) {
    for (int oldInterface : oldInterfaces) {
      boolean found = false;
      for (int newInterface : newInterfaces) {
        found = oldInterface == newInterface;
        if (found) {
          break;
        }
      }
      if (!found) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return a map [fieldName->FieldInfo]
   */
  private static IntObjectMap<FieldInfo> getFieldInfos(Cache cache, int qName) throws CacheCorruptedException {
    final IntObjectMap<FieldInfo> map = IntMaps.newIntObjectHashMap();
    for (FieldInfo fieldInfo : cache.getFields(qName)) {
      map.put(fieldInfo.getName(), fieldInfo);
    }
    return map;
  }

  /**
   * @return a map [methodSignature->MethodInfo]
   */
  private Map<String, MethodInfoContainer> getMethodInfos(final MethodInfo[] methods) throws CacheCorruptedException {
    final Map<String, MethodInfoContainer> map = new HashMap<String, MethodInfoContainer>();
    final SymbolTable symbolTable = myJavaDependencyCache.getSymbolTable();
    for (MethodInfo methodInfo : methods) {
      final String signature = methodInfo.getDescriptor(symbolTable);
      final MethodInfoContainer currentValue = map.get(signature);
      // covariant methods have the same signature, so there might be several MethodInfos for one key
      if (currentValue == null) {
        map.put(signature, new MethodInfoContainer(methodInfo));
      } else {
        currentValue.add(methodInfo);
      }
    }
    return map;
  }

  private static void addAddedMembers(IntObjectMap<FieldInfo> oldFields, Map<String, MethodInfoContainer> oldMethods,
                                      IntObjectMap<FieldInfo> newFields, Map<String, MethodInfoContainer> newMethods,
                                      Collection<MemberInfo> members) {

    newFields.forEach((fieldName, fieldInfo) ->
    {
      if (!oldFields.containsKey(fieldName)) {
        members.add(fieldInfo);
      }
    });
    for (final String signature : newMethods.keySet()) {
      if (!oldMethods.containsKey(signature)) {
        members.addAll(newMethods.get(signature).getMethods());
      }
    }
  }

  private static void addRemovedMembers(IntObjectMap<FieldInfo> oldFields, Map<String, MethodInfoContainer> oldMethods,
                                        IntObjectMap<FieldInfo> newFields, Map<String, MethodInfoContainer> newMethods,
                                        Collection<MemberInfo> members) {
    addAddedMembers(newFields, newMethods, oldFields, oldMethods, members);
  }

  private void addChangedMembers(IntObjectMap<FieldInfo> oldFields, Map<String, MethodInfoContainer> oldMethods,
                                 IntObjectMap<FieldInfo> newFields, Map<String, MethodInfoContainer> newMethods,
                                 Collection<MemberInfo> members) throws CacheCorruptedException {
    oldFields.forEach((fieldName, oldInfo) ->
    {
      final FieldInfo newInfo = newFields.get(fieldName);
      if (newInfo != null) {
        final FieldChangeDescription changeDescription = new FieldChangeDescription(oldInfo, newInfo);
        if (changeDescription.isChanged()) {
          members.add(oldInfo);
          myChangeDescriptions.put(oldInfo, changeDescription);
        }
      }
    });

    if (!oldMethods.isEmpty()) {
      final SymbolTable symbolTable = myJavaDependencyCache.getSymbolTable();
      final Set<MethodInfo> processed = new HashSet<MethodInfo>();
      for (final String signature : oldMethods.keySet()) {
        final MethodInfoContainer oldMethodsContainer = oldMethods.get(signature);
        final MethodInfoContainer newMethodsContainer = newMethods.get(signature);
        if (newMethodsContainer != null) {
          processed.clear();
          if (oldMethodsContainer.size() == newMethodsContainer.size()) {
            // first, process all corresponding method infos
            for (MethodInfo oldInfo : oldMethodsContainer.getMethods()) {
              MethodInfo _newInfo = null;
              for (MethodInfo newInfo : newMethodsContainer.getMethods()) {
                if (oldInfo.isNameAndDescriptorEqual(newInfo)) {
                  _newInfo = newInfo;
                  break;
                }
              }
              if (_newInfo != null) {
                processed.add(oldInfo);
                processed.add(_newInfo);
                final MethodChangeDescription changeDescription = new MethodChangeDescription(oldInfo, _newInfo, symbolTable);
                if (changeDescription.isChanged()) {
                  members.add(oldInfo);
                  myChangeDescriptions.put(oldInfo, changeDescription);
                }
              }
            }
          }
          // processing the rest of infos, each pair
          for (MethodInfo oldInfo : oldMethodsContainer.getMethods()) {
            if (processed.contains(oldInfo)) {
              continue;
            }
            for (MethodInfo newInfo : newMethodsContainer.getMethods()) {
              if (processed.contains(newInfo)) {
                continue;
              }
              final MethodChangeDescription changeDescription = new MethodChangeDescription(oldInfo, newInfo, symbolTable);
              if (changeDescription.isChanged()) {
                members.add(oldInfo);
                myChangeDescriptions.put(oldInfo, changeDescription);
              }
            }
          }
        }
      }
    }
  }

  private boolean hasEquivalentMethod(Collection<MethodInfo> members, Dependency.MethodRef modelMethod) throws CacheCorruptedException {
    final String[] modelSignature = modelMethod.getParameterDescriptors(myJavaDependencyCache.getSymbolTable());
    for (final MethodInfo method : members) {
      if (modelMethod.name != method.getName()) {
        continue;
      }
      final String[] methodSignature = method.getParameterDescriptors(myJavaDependencyCache.getSymbolTable());
      if (modelSignature.length != methodSignature.length) {
        continue;
      }

      for (int i = 0; i < methodSignature.length; i++) {
        if (!methodSignature[i].equals(modelSignature[i])) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Equivalent: " + modelMethod.getDescriptor(myJavaDependencyCache.getSymbolTable()) + " <=> " +
                method.getDescriptor(myJavaDependencyCache.getSymbolTable()));
          }
          return true;
        }
      }
    }
    return false;
  }

  private static
  @NonNls
  String signatureToSourceTypeName(String signature) {
    try {
      switch (signature.charAt(0)) {
        case 'B':
          return "byte";
        case 'C':
          return "char";
        case 'D':
          return "double";
        case 'F':
          return "float";
        case 'I':
          return "int";
        case 'J':
          return "long";

        case 'L': { // Full class name
          int index = signature.indexOf(';'); // Look for closing `;'

          if (index < 0) {
            throw new RuntimeException("Invalid signature: " + signature);
          }

          return signature.substring(1, index).replace('/', '.');
        }

        case 'S':
          return "short";
        case 'Z':
          return "boolean";

        case '[': { // Array declaration
          int n;
          StringBuffer brackets;
          String type;

          brackets = new StringBuffer(); // Accumulate []'s

          // Count opening brackets and look for optional size argument
          for (n = 0; signature.charAt(n) == '['; n++) {
            brackets.append("[]");
          }


          // The rest of the string denotes a `<field_type>'
          type = signatureToSourceTypeName(signature.substring(n));

          return type + brackets.toString();
        }

        case 'V':
          return "void";

        default:
          throw new RuntimeException("Invalid signature: `" +
              signature + "'");
      }
    } catch (StringIndexOutOfBoundsException e) { // Should never occur
      throw new RuntimeException("Invalid signature: " + e + ":" + signature);
    }
  }

  private Dependency[] getBackDependencies() throws CacheCorruptedException {
    if (myBackDependencies == null) {
      myBackDependencies = myJavaDependencyCache.getCache().getBackDependencies(myQName);
    }
    return myBackDependencies;
  }

  private static class MethodInfoContainer {
    private List<MethodInfo> myInfos = null;

    protected MethodInfoContainer(MethodInfo info) {
      myInfos = Collections.singletonList(info);
    }

    public List<MethodInfo> getMethods() {
      return myInfos;
    }

    public int size() {
      return myInfos.size();
    }

    public void add(MethodInfo info) {
      if (myInfos.size() == 1) {
        myInfos = new ArrayList<MethodInfo>(myInfos);
      }
      myInfos.add(info);
    }
  }
}
