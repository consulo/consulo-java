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
package com.intellij.java.impl.refactoring.rename.naming;

import consulo.usage.RenameableUsage;
import consulo.util.lang.StringUtil;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author peter
 */
public abstract class AutomaticUsageRenamer<T> {
  private final String myOldName;
  private final String myNewName;
  private final Map<T, String> myRenames = new LinkedHashMap<T, String>();
  private final List<T> myElements = new ArrayList<T>();
  private final Map<T, List<RenameableUsage>> myReferences = new HashMap<T, List<RenameableUsage>>();

  protected AutomaticUsageRenamer(List<? extends T> renamedElements, String oldName, String newName) {
    myOldName = oldName;
    myNewName = newName;
    List<T> elements = new ArrayList<T>(renamedElements);
    Collections.sort(elements, new Comparator<T>() {
      public int compare(T o1, T o2) {
        int i = StringUtil.compare(getSourceName(o1), getSourceName(o2), false);
        if (i != 0) return i;
        return getName(o1).compareTo(getName(o2));
      }
    });
    for (T element : elements) {
      String suggestedNewName = suggestName(element);
      if (!getName(element).equals(suggestedNewName)) {
        myElements.add(element);
        setRename(element, suggestedNewName);
      }
    }
  }

  public boolean hasAnythingToRename() {
    for (final String s : myRenames.values()) {
      if (s != null) return true;
    }
    return false;
  }

  public boolean isEmpty() {
    return myRenames.isEmpty();
  }

  protected String getOldName() {
    return myOldName;
  }

  public String getNewName() {
    return myNewName;
  }

  protected boolean isChecked(T element) {
    return myRenames.containsKey(element);
  }

  protected boolean isCheckedInitially(T element) {
    return false;
  }

  protected boolean isNameAlreadySuggested(String newName) {
    return myRenames.values().contains(newName);
  }

  public List<? extends T> getElements() {
    return myElements;
  }

  @Nullable
  /**
   * Element source, path. For example, package. Taken into account while sorting.
   */
  public String getSourceName(T element) {
    return null;
  }

  public String getNewElementName(T element) {
    return myRenames.get(element);
  }

  public Map<? extends T,String> getRenames() {
    return myRenames;
  }

  public void setRename(T element, @Nonnull String replacement) {
    myRenames.put(element, replacement);
  }

  public void doNotRename(T element) {
    myRenames.remove(element);
  }

  @Nullable
  public String getErrorText(T element) {
    return null;
  }

  public final void doRename() throws IncorrectOperationException {
    for (final Map.Entry<T, List<RenameableUsage>> entry : myReferences.entrySet()) {
      final T element = entry.getKey();
      final String newName = getNewElementName(element);
      doRenameElement(element);
      for (final RenameableUsage usage : entry.getValue()) {
        usage.rename(newName);
      }
    }
  }

  protected abstract void doRenameElement(T element) throws IncorrectOperationException;

  protected abstract String suggestName(T element);

  protected abstract String getName(T element);

  public abstract String getDialogTitle();

  public abstract String getDialogDescription();

  public abstract String getEntityName();
}

