// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.language.psi.ExternallyDefinedPsiElement;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import consulo.util.interner.Interner;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClassInnerStuffCache {
  private final PsiExtensibleClass myClass;
  private final Ref<Pair<Long, Interner<PsiMember>>> myInterner = Ref.create();

  public ClassInnerStuffCache(@Nonnull PsiExtensibleClass aClass) {
    myClass = aClass;
  }

  @Nonnull
  public PsiMethod[] getConstructors() {
    return copy(LanguageCachedValueUtil.getProjectPsiDependentCache(myClass, PsiImplUtil::getConstructors));
  }

  @Nonnull
  public PsiField[] getFields() {
    return copy(LanguageCachedValueUtil.getProjectPsiDependentCache(myClass, __ -> calcFields()));
  }

  @Nonnull
  public PsiMethod[] getMethods() {
    return copy(LanguageCachedValueUtil.getProjectPsiDependentCache(myClass, __ -> calcMethods()));
  }

  @Nonnull
  public PsiClass[] getInnerClasses() {
    return copy(LanguageCachedValueUtil.getProjectPsiDependentCache(myClass, __ -> calcInnerClasses()));
  }

  @Nonnull
  public PsiRecordComponent[] getRecordComponents() {
    return copy(LanguageCachedValueUtil.getProjectPsiDependentCache(myClass, __ -> calcRecordComponents()));
  }

  @Nullable
  public PsiField findFieldByName(String name, boolean checkBases) {
    if (checkBases) {
      return PsiClassImplUtil.findFieldByName(myClass, name, true);
    } else {
      return LanguageCachedValueUtil.getProjectPsiDependentCache(myClass, __ -> getFieldsMap()).get(name);
    }
  }

  @Nonnull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    if (checkBases) {
      return PsiClassImplUtil.findMethodsByName(myClass, name, true);
    } else {
      return copy(ObjectUtil.notNull(LanguageCachedValueUtil.getProjectPsiDependentCache(myClass, __ -> getMethodsMap()).get(name), PsiMethod.EMPTY_ARRAY));
    }
  }

  @Nullable
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    if (checkBases) {
      return PsiClassImplUtil.findInnerByName(myClass, name, true);
    } else {
      return LanguageCachedValueUtil.getProjectPsiDependentCache(myClass, __ -> getInnerClassesMap()).get(name);
    }
  }

  private boolean classNameIsSealed() {
    return PsiUtil.getLanguageLevel(myClass).isAtLeast(LanguageLevel.JDK_17) && PsiKeyword.SEALED.equals(myClass.getName());
  }

  private boolean isAnonymousClass() {
    return myClass.getName() == null || myClass instanceof PsiAnonymousClass;
  }

  private static <T> T[] copy(T[] value) {
    return value.length == 0 ? value : value.clone();
  }

  @Nonnull
  private PsiField[] calcFields() {
    List<PsiField> own = myClass.getOwnFields();
    List<PsiField> ext = internMembers(PsiAugmentProvider.collectAugments(myClass, PsiField.class, null));
    return ArrayUtil.mergeCollections(own, ext, PsiField.ARRAY_FACTORY);
  }

  @Nonnull
  private <T extends PsiMember> List<T> internMembers(List<T> members) {
    return ContainerUtil.map(members, this::internMember);
  }

  private <T extends PsiMember> T internMember(T m) {
    if (m == null) {
      return null;
    }
    long modCount = myClass.getManager().getModificationTracker().getModificationCount();
    synchronized (myInterner) {
      Pair<Long, Interner<PsiMember>> pair = myInterner.get();
      if (pair == null || pair.first.longValue() != modCount) {
        myInterner.set(pair = Pair.create(modCount, Interner.createWeakInterner()));
      }
      //noinspection unchecked
      return (T) pair.second.intern(m);
    }
  }

  @Nonnull
  private PsiMethod[] calcMethods() {
    List<PsiMethod> own = myClass.getOwnMethods();
    List<PsiMethod> ext = internMembers(PsiAugmentProvider.collectAugments(myClass, PsiMethod.class, null));
    return ArrayUtil.mergeCollections(own, ext, PsiMethod.ARRAY_FACTORY);
  }

  @Nonnull
  private PsiClass[] calcInnerClasses() {
    List<PsiClass> own = myClass.getOwnInnerClasses();
    List<PsiClass> ext = internMembers(PsiAugmentProvider.collectAugments(myClass, PsiClass.class, null));
    return ArrayUtil.mergeCollections(own, ext, PsiClass.ARRAY_FACTORY);
  }

  @Nonnull
  private PsiRecordComponent[] calcRecordComponents() {
    PsiRecordHeader header = myClass.getRecordHeader();
    return header == null ? PsiRecordComponent.EMPTY_ARRAY : header.getRecordComponents();
  }

  @Nonnull
  private Map<String, PsiField> getFieldsMap() {
    Map<String, PsiField> cachedFields = new HashMap<>();
    for (PsiField field : myClass.getOwnFields()) {
      String name = field.getName();
      if (!cachedFields.containsKey(name)) {
        cachedFields.put(name, field);
      }
    }
    return ConcurrentFactoryMap.createMap(name -> {
      PsiField result = cachedFields.get(name);
      return result != null ? result :
          internMember(ContainerUtil.getFirstItem(PsiAugmentProvider.collectAugments(myClass, PsiField.class, name)));
    });
  }

  @Nonnull
  private Map<String, PsiMethod[]> getMethodsMap() {
    List<PsiMethod> ownMethods = myClass.getOwnMethods();
    return ConcurrentFactoryMap.createMap(name -> {
      return JBIterable
          .from(ownMethods).filter(m -> name.equals(m.getName()))
          .append(internMembers(PsiAugmentProvider.collectAugments(myClass, PsiMethod.class, name)))
          .toList()
          .toArray(PsiMethod.EMPTY_ARRAY);
    });
  }

  @Nonnull
  private Map<String, PsiClass> getInnerClassesMap() {
    Map<String, PsiClass> cachedInners = new HashMap<>();
    for (PsiClass psiClass : myClass.getOwnInnerClasses()) {
      String name = psiClass.getName();
      if (name == null) {
        Logger.getInstance(ClassInnerStuffCache.class).error(psiClass);
      } else if (!(psiClass instanceof ExternallyDefinedPsiElement) || !cachedInners.containsKey(name)) {
        cachedInners.put(name, psiClass);
      }
    }
    return ConcurrentFactoryMap.createMap(name -> {
      PsiClass result = cachedInners.get(name);
      return result != null ? result :
          internMember(ContainerUtil.getFirstItem(PsiAugmentProvider.collectAugments(myClass, PsiClass.class, name)));
    });
  }

  /**
   * @deprecated does nothing
   */
  @Deprecated
  public void dropCaches() {
  }
}