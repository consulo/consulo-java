/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.xml.GenericValue;
import consulo.psi.PsiPackage;

/**
 * @author peter
 */
public abstract class ReadOnlyGenericValue<T> implements GenericValue<T>
{

  public static GenericValue<?> NULL = getInstance(null);
  public static <T> GenericValue<T> nullInstance() { return (GenericValue<T>)NULL; }

  public static <T> GenericValue<T> getInstance(final T value) {
    return new ReadOnlyGenericValue<T>() {
      public T getValue() {
        return value;
      }
    };
  }

  public static <T> GenericValue<T> getInstance(final T value, final String stringValue) {
      return new ReadOnlyGenericValue<T>() {
        @Override
        public String getStringValue() {
          return stringValue;
        }

        public T getValue() {
          return value;
        }
      };
    }

  public final void setStringValue(final String value) {
    throw new UnsupportedOperationException("Model is read-only");
  }

  public String getStringValue() {
    final T value = getValue();
    if (value == null) return null;
    if (value instanceof String) return (String)value;
    if (value instanceof PsiClass) return ((PsiClass)value).getQualifiedName();
    if (value instanceof PsiPackage) return ((PsiPackage)value).getQualifiedName();
    if (value instanceof PsiNamedElement) return ((PsiNamedElement)value).getName();
    return value.toString();
  }

  public final void setValue(final T value) {
    throw new UnsupportedOperationException("Model is read-only");
  }

  public String toString() {
    return getStringValue();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final Object value1 = ((ReadOnlyGenericValue)o).getValue();
    final Object value = getValue();
    if (value != null ? !value.equals(value1) : value1 != null) return false;

    final Object str1 = ((ReadOnlyGenericValue)o).getStringValue();
    final Object str = getStringValue();
    if (str != null ? !str.equals(str1) : str1 != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    final Object value = getValue();
    if (value != null) return value.hashCode();

    final String str = getStringValue();
    return str != null ? str.hashCode() : 0;
  }
}
