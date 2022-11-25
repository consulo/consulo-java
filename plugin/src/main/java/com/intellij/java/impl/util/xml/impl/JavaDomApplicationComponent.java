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
package com.intellij.java.impl.util.xml.impl;

import com.intellij.java.impl.util.xml.*;
import com.intellij.java.impl.util.xml.converters.values.ClassArrayConverter;
import com.intellij.java.impl.util.xml.converters.values.ClassValueConverter;
import com.intellij.java.impl.util.xml.ui.PsiClassControl;
import com.intellij.java.impl.util.xml.ui.PsiClassTableCellEditor;
import com.intellij.java.impl.util.xml.ui.PsiTypeControl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import consulo.xml.util.xml.ConverterManager;
import consulo.xml.util.xml.ui.DomUIFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.function.Consumer;

/**
 * @author peter
 */
@Singleton
public class JavaDomApplicationComponent implements Consumer<DomUIFactory> {
  @Inject
  public JavaDomApplicationComponent(ConverterManager converterManager) {
    converterManager.addConverter(PsiClass.class, new PsiClassConverter());
    converterManager.addConverter(PsiType.class, new CanonicalPsiTypeConverterImpl());
    converterManager.registerConverterImplementation(JvmPsiTypeConverter.class, new JvmPsiTypeConverterImpl());
    converterManager.registerConverterImplementation(CanonicalPsiTypeConverter.class, new CanonicalPsiTypeConverterImpl());

    final ClassValueConverter classValueConverter = ClassValueConverter.getClassValueConverter();
    converterManager.registerConverterImplementation(ClassValueConverter.class, classValueConverter);
    final ClassArrayConverter classArrayConverter = ClassArrayConverter.getClassArrayConverter();
    converterManager.registerConverterImplementation(ClassArrayConverter.class, classArrayConverter);
  }

  @Override
  public void accept(DomUIFactory factory) {
    factory.registerCustomControl(PsiClass.class, wrapper -> new PsiClassControl(wrapper, false));
    factory.registerCustomControl(PsiType.class, wrapper -> new PsiTypeControl(wrapper, false));

    factory.registerCustomCellEditor(PsiClass.class, element -> new PsiClassTableCellEditor(element.getManager().getProject(), element.getResolveScope()));
  }
}