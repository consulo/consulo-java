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
package com.intellij.jam.model.common;

import com.intellij.jam.JamElement;
import com.intellij.java.language.psi.PsiMember;
import consulo.application.progress.ProgressManager;
import consulo.language.pom.PomRenameableTarget;
import consulo.language.pom.PomService;
import consulo.language.pom.PomTarget;
import consulo.language.pom.PomTargetPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiTarget;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.util.collection.ContainerUtil;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomUtil;
import consulo.xml.util.xml.GenericValue;
import consulo.xml.util.xml.MergedObject;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * @author Gregory.Shrago
 */
public class ManualModelMergerUtil {
  private ManualModelMergerUtil() {
  }

  public static <T, V> List<T> join(V[] list, Joiner<V, T> joiner) {
    return join(Arrays.asList(list), joiner);
  }

  public static <T, V> List<T> join(Iterable<? extends V> list, Joiner<V, T> joiner) {
    final Set<T> notToBeMergedSet = new HashSet<T>();
    final LinkedHashMap<Object, T> map = new LinkedHashMap<Object,T>();
    for (final V v : list) {
      ProgressManager.checkCanceled();
      for (T t : joiner.map(v)) {
        final Object key = joiner.key(t);
        final T prev = map.get(key);
        if (notToBeMergedSet.contains(prev)) continue;
        map.put(key, joiner.join(prev, t, notToBeMergedSet));
      }
    }
    return new ArrayList<T>(map.values());
  }

  public static <T, V, X extends T> List<GenericValue<X>> joinValues(Iterable<? extends V> list, final Function<V, Collection<? extends GenericValue<X>>> mapper) {
    return join(list, new Joiner<V, GenericValue<X>>() {
      @Override
      public Collection<? extends GenericValue<X>> map(final V v) {
        return mapper.apply(v);
      }

      @Override
      public Object key(final GenericValue<X> value) {
        return value.getValue();
      }

      @Override
      @Nonnull
      public GenericValue<X> join(@Nullable final GenericValue<X> prev, final GenericValue<X> next,
                                  final Collection<GenericValue<X>> notToBeMergedSet) {
        return prev == null? next : prev instanceof MyGenericValue? ((MyGenericValue<X>)prev).addImplementation(next) : new MyGenericValue<X>(prev, next);
      }
    });
  }

  public static <T, V, X extends T> GenericValue<X> joinValue(Iterable<? extends V> list, Function<V, GenericValue<X>> mapper) {
    GenericValue<X> prev = null;
    for (final V v : list) {
      final GenericValue<X> value = mapper.apply(v);
      if (prev == null) prev = value;
      else if (prev instanceof MyGenericValue) ((MyGenericValue<X>)prev).addImplementation(value);
      else prev = new MyGenericValue<X>(prev, value);
    }
    assert prev != null;
    return prev;
  }

  public static <T, V> T findDom(Iterable<? extends V> list, Function<V, T> mapper, T defValue) {
    for (V v : list) {
      if (v instanceof DomElement) {
        return mapper.apply(v);
      }
    }
    return defValue;
  }

  public static <T, V> V findLast(List<? extends T> list, Function<T, V> mapping, V defValue) {
    final ListIterator<? extends T> listIterator = list.listIterator(list.size());
    while (listIterator.hasPrevious()) {
      final V v = mapping.apply(listIterator.previous());
      if (v != null) return v;
    }
    return defValue;
  }

  public static <T, V, X> X findLast(List<? extends T> list, Function<T, V> mapping, Function<T, X> resultMapping, X defValue) {
    final ListIterator<? extends T> listIterator = list.listIterator(list.size());
    while (listIterator.hasPrevious()) {
      final T dom = listIterator.previous();
      final V v = mapping.apply(dom);
      if (v != null) return resultMapping.apply(dom);
    }
    return defValue;
  }

  public interface Joiner<V, T> {
    Collection<? extends T> map(V v);
    @Nullable
    Object key(T t);
    @Nonnull
    T join(@Nullable T prev, T next, Collection<T> notToBeMergedSet);
  }

  public static abstract class SimpleJoiner<V, T extends CommonModelElement> implements Joiner<V, T> {
    @Override
    @Nonnull
    public final T join(@Nullable T prev, T next, final Collection<T> notToBeMergedSet) {
      return prev == null ? next : prev instanceof MyMergedObject ? ((MyMergedObject<T>)prev).addImplementation(next) : createMergedImplementation(prev, next);
    }

    @Nonnull
    protected abstract T createMergedImplementation(T prev, T next);
  }

  public static abstract class NextJoiner<V, T> implements Joiner<V, T> {
    @Override
    @Nonnull
    public final T join(@Nullable T prev, T next, final Collection<T> notToBeMergedSet) {
      return next;
    }
  }

  public static abstract class AnnoJoiner<V, T extends CommonModelElement, Psi extends PsiMember> implements Joiner<V, T> {
    @Override
    @Nonnull
    public final T join(@Nullable T prev, T next, final Collection<T> notToBeMergedSet) {
      if (shouldNotBeMerged(next)) {
        notToBeMergedSet.add(next);
        return next;
      }
      return joinInner(prev, next);
    }

    @Nonnull
    protected T joinInner(@Nullable T prev, T next) {
      if (prev instanceof MyMergedObject) return ((MyMergedObject<T>)prev).addImplementation(next);
      if (prev != null) return createMergedImplementation(prev, next);
      if (next instanceof JamElement) return next;
      final Psi psiMember = getPsiMember(next);
      final T anno;
      if (psiMember == null || (anno = getCurrentJam(next, psiMember)) == null) return next;
      return createMergedImplementation(anno, next);
    }

    @Nullable
    protected abstract T getCurrentJam(final T next, final Psi psiMember);

    @Nonnull
    protected abstract T createMergedImplementation(T prev, T next);
    @Nullable
    protected abstract Psi getPsiMember(T element);

    protected boolean shouldNotBeMerged(T element) {
      return false;
    }
  }

  public static class MyMergedObject<T extends CommonModelElement> implements MergedObject<T>, CommonModelElement {
    protected final List<T> myTs;

    protected MyMergedObject(final T... ts) {
      assert ts.length > 0;
      myTs = new ArrayList<T>(ts.length);
      ContainerUtil.addAll(myTs, ts);
    }

    protected MyMergedObject(final List<T> ts) {
      assert !ts.isEmpty();
      myTs = ts;
    }

    @Override
    public List<T> getImplementations() {
      return myTs;
    }

    public T addImplementation(final T next) {
      myTs.add(next);
      return (T)this;
    }

    @Override
    public boolean isValid() {
      for (T t : myTs) {
        if (!t.isValid()) return false;
      }
      return true;
    }

    @Override
    public XmlTag getXmlTag() {
      return findDom(myTs, new Function<T, XmlTag>() {
        public XmlTag apply(final T t) {
          return t.getXmlTag();
        }
      }, null);
    }

    @Override
    public PsiManager getPsiManager() {
      return myTs.get(0).getPsiManager();
    }

    @Override
    public Module getModule() {
      for (T t : myTs) {
        final PsiElement element = t.getIdentifyingPsiElement();
        if (element != null) {
          final Module module = ModuleUtilCore.findModuleForPsiElement(element);
          if (module != null) {
            return module;
          }
        }
      }
      return null;
    }

    @Override
    public PsiElement getIdentifyingPsiElement() {
      if (myTs.size() == 1) return myTs.get(0).getIdentifyingPsiElement();
      final List<? extends PomTarget> targets = getPomTargets(this);
      if (targets.isEmpty()) return myTs.get(0).getIdentifyingPsiElement();

      boolean notRenameable = false;
      for (PomTarget target : targets) {
        if (!(target instanceof PomRenameableTarget)) {
          notRenameable = true;
          break;
        }
      }
      final PomTarget target =
        targets.size() == 1? targets.get(0) :
          notRenameable? new MyTarget<PomTarget>(this, (List<PomTarget>)targets) :
          new MyRenameableTarget(this, (List<PomRenameableTarget>)targets);
      return PomService.convertToPsi(getPsiManager().getProject(), target);
    }

    @Override
    public PsiFile getContainingFile() {
      final PsiElement element = getIdentifyingPsiElement();
      return element == null? null : element.getContainingFile();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MyMergedObject object = (MyMergedObject)o;

      if (!myTs.equals(object.myTs)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myTs.hashCode();
    }

  }

  private static <T extends CommonModelElement> List<PomTarget> getPomTargets(MyMergedObject<T> object) {
    return ContainerUtil.mapNotNull(object.getImplementations(), new Function<T, PomTarget>() {
      public PomTarget apply(final T t) {
        final PsiElement element = t.getIdentifyingPsiElement();
        if (element instanceof PomTarget) return (PomTarget)element;
        if (element instanceof PomTargetPsiElement) {
          return ((PomTargetPsiElement)element).getTarget();
        }
        return null;
      }
    });
  }

  public static class MyTarget<T extends PomTarget> implements MergedObject<T>, CommonModelTarget {
    private final CommonModelElement myObject;
    protected final List<T> myTargets;

    public MyTarget(CommonModelElement object, final List<T> targets) {
      myObject = object;
      myTargets = targets;
    }

    @Override
    public CommonModelElement getCommonElement() {
      return myObject;
    }

    @Override
    public boolean isValid() {
      for (PomTarget target : myTargets) {
        if (!target.isValid()) return false;
      }
      return true;
    }

    @Override
    public void navigate(final boolean requestFocus) {
      final PomTarget target = findLast(myTargets, new Function<T, PomTarget>() {
        public PomTarget apply(final PomTarget target) {
          return target.canNavigate() ? target : null;
        }
      }, null);
      if (target != null) target.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return findLast(myTargets, new Function<PomTarget, PomTarget>() {
        public PomTarget apply(final PomTarget target) {
          return target.canNavigate() ? target : null;
        }
      }, null) != null;
    }

    @Override
    public boolean canNavigateToSource() {
      return findLast(myTargets, new Function<PomTarget, PomTarget>() {
        public PomTarget apply(final PomTarget target) {
          return target.canNavigateToSource() ? target : null;
        }
      }, null) != null;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof PomTarget)) return false;
      final PomTarget target = (PomTarget)o;

      if (target instanceof MyTarget) {
        return myTargets.equals(((MyTarget)target).myTargets);
      }

      return myTargets.contains(target);
    }

    @Override
    public int hashCode() {
      return myTargets.hashCode();
    }

    @Override
    @Nonnull
    public PsiElement getNavigationElement() {
      return findLast(myTargets, new Function<T, PsiElement>() {
        public PsiElement apply(final T t) {
          return t instanceof PsiTarget ? ((PsiTarget)t).getNavigationElement() : null;
        }
      }, null);
    }

    @Override
    public List<T> getImplementations() {
      return myTargets;
    }
  }

  public static class MyRenameableTarget extends MyTarget<PomRenameableTarget> implements PomRenameableTarget<MyRenameableTarget> {
    public MyRenameableTarget(CommonModelElement object, final List<PomRenameableTarget> targets) {
      super(object, targets);
    }

    @Override
    public boolean isWritable() {
      for (PomRenameableTarget target : myTargets) {
        if (!target.isWritable()) return false;
      }
      return true;
    }

    @Override
    public MyRenameableTarget setName(@Nonnull final String newName) {
      final ArrayList<PomRenameableTarget> list = new ArrayList<PomRenameableTarget>(myTargets.size());
      for (PomRenameableTarget target : myTargets) {
        final Object result = target.setName(newName);
        if (result instanceof PomRenameableTarget) {
          list.add((PomRenameableTarget)result);
        }
      }
      return new MyRenameableTarget(getCommonElement(), list);
    }

    @Override
    public String getName() {
      final PomRenameableTarget target = findLast(myTargets, new Function<PomRenameableTarget, PomRenameableTarget>() {
        public PomRenameableTarget apply(final PomRenameableTarget target) {
          return target.getName() != null ? target : null;
        }
      }, null);
      return target == null? null : target.getName();
    }
  }

  public static class MyGenericValue<T> implements MergedObject<GenericValue<? extends T>>, GenericValue<T> {

    final List<GenericValue<? extends T>> myTs;

    MyGenericValue(final GenericValue<? extends T>... ts) {
      assert ts.length > 0;
      myTs = new ArrayList<GenericValue<? extends T>>(ts.length);
      ContainerUtil.addAll(myTs, ts);
    }

    @Override
    public List<GenericValue<? extends T>> getImplementations() {
      return myTs;
    }

    public GenericValue<T> addImplementation(final GenericValue<T> next) {
      myTs.add(next);
      return this;
    }

    @Override
    public String getStringValue() {
      return findLast(myTs, new Function<GenericValue<? extends T>, String>() {
        public String apply(final GenericValue<? extends T> value) {
          return value.getStringValue();
        }
      }, null);
    }

    @Override
    public T getValue() {
      return findLast(myTs, new Function<GenericValue<? extends T>, T>() {
        public T apply(final GenericValue<? extends T> value) {
          if (value instanceof DomElement && !DomUtil.hasXml(((DomElement)value))) return null;
          return value.getValue();
        }
      }, null);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MyGenericValue value = (MyGenericValue)o;

      if (!myTs.equals(value.myTs)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myTs.hashCode();
    }
  }
}
