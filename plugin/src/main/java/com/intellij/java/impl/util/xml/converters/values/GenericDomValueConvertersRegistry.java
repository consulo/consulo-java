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
package com.intellij.java.impl.util.xml.converters.values;

import com.intellij.java.language.psi.PsiType;
import consulo.component.extension.ExtensionPointName;
import consulo.component.extension.Extensions;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.function.Condition;
import consulo.xml.util.xml.Converter;
import consulo.xml.util.xml.GenericDomValue;
import consulo.xml.util.xml.converters.values.BooleanValueConverter;
import consulo.xml.util.xml.converters.values.CharacterValueConverter;
import consulo.xml.util.xml.converters.values.NumberValueConverter;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: Sergey.Vasiliev
 */
public class GenericDomValueConvertersRegistry {

  public interface Provider {
    Converter getConverter();

    Condition<Pair<PsiType, GenericDomValue>> getCondition();
  }

  public void registerFromExtensions(ExtensionPointName<Provider> extensionPointName) {
    Provider[] providers = Extensions.getExtensions(extensionPointName);
    for (Provider provider : providers) {
      registerConverter(provider.getConverter(), provider.getCondition());
    }
  }

  private final Map<Condition<Pair<PsiType, GenericDomValue>>, Converter<?>> myConditionConverters =
      new LinkedHashMap<Condition<Pair<PsiType, GenericDomValue>>, Converter<?>>();

  public void registerDefaultConverters() {
    registerBooleanConverters();

    registerCharacterConverter();

    registerNumberValueConverters();

    registerClassValueConverters();
  }

  private void registerBooleanConverters() {
    registerConverter(new BooleanValueConverter(false), PsiType.BOOLEAN);
    registerConverter(new BooleanValueConverter(true), Boolean.class);
  }

  public void registerClassValueConverters() {
    registerConverter(ClassValueConverter.getClassValueConverter(), Class.class);
    registerConverter(ClassArrayConverter.getClassArrayConverter(), Class[].class);
  }

  public void registerCharacterConverter() {
    registerConverter(new CharacterValueConverter(false), PsiType.CHAR);
    registerConverter(new CharacterValueConverter(true), Character.class);
  }

  public void registerNumberValueConverters() {
    registerConverter(new NumberValueConverter(byte.class, false), PsiType.BYTE);
    registerConverter(new NumberValueConverter(Byte.class, true), Byte.class);

    registerConverter(new NumberValueConverter(short.class, false), PsiType.SHORT);
    registerConverter(new NumberValueConverter(Short.class, true), Short.class);

    registerConverter(new NumberValueConverter(int.class, false), PsiType.INT);
    registerConverter(new NumberValueConverter(Integer.class, true), Integer.class);

    registerConverter(new NumberValueConverter(long.class, false), PsiType.LONG);
    registerConverter(new NumberValueConverter(Long.class, true), Long.class);

    registerConverter(new NumberValueConverter(float.class, false), PsiType.FLOAT);
    registerConverter(new NumberValueConverter(Float.class, true), Float.class);

    registerConverter(new NumberValueConverter(double.class, false), PsiType.DOUBLE);
    registerConverter(new NumberValueConverter(Double.class, true), Double.class);

    registerConverter(new NumberValueConverter(BigDecimal.class, true), BigDecimal.class);
    registerConverter(new NumberValueConverter(BigInteger.class, true), BigInteger.class);
  }

  public void registerConverter(@Nonnull Converter<?> provider, @Nonnull final PsiType type) {
    registerConverter(provider, new Condition<Pair<PsiType, GenericDomValue>>() {
      public boolean value(final Pair<PsiType, GenericDomValue> pair) {
        return Comparing.equal(pair.getFirst(), type);
      }
    });
  }

  public void registerConverter(@Nonnull Converter<?> provider, @Nonnull Condition<Pair<PsiType, GenericDomValue>> condition) {
    myConditionConverters.put(condition, provider);
  }

  @Nullable
  public Converter<?> getConverter(@Nonnull GenericDomValue domValue, @Nullable PsiType type) {
    final Pair<PsiType, GenericDomValue> pair = new Pair<PsiType, GenericDomValue>(type, domValue);
    for (@Nonnull Condition<Pair<PsiType, GenericDomValue>> condition : myConditionConverters.keySet()) {
      if (condition.value(pair)) {
        return myConditionConverters.get(condition);
      }
    }
    return null;
  }

  public void registerConverter(@Nonnull Converter<?> provider, @Nonnull Class type) {
    final String name = type.getCanonicalName();
    registerConverter(provider, new Condition<Pair<PsiType, GenericDomValue>>() {
      public boolean value(final Pair<PsiType, GenericDomValue> pair) {
        return pair.first != null && Comparing.equal(name, pair.first.getCanonicalText());
      }
    });
  }

}
