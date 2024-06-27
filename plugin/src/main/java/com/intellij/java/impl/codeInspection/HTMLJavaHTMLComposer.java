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

/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.java.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.language.JavaLanguage;
import consulo.language.Language;
import consulo.language.editor.inspection.HTMLComposerExtension;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.util.dataholder.Key;

public abstract class HTMLJavaHTMLComposer implements HTMLComposerExtension<HTMLJavaHTMLComposer> {
  public static final Key<HTMLJavaHTMLComposer> COMPOSER = Key.create("HTMLJavaComposer");

  public abstract void appendClassOrInterface(StringBuffer buf, RefClass refClass, boolean capitalizeFirstLetter);

  public static String getClassOrInterface(RefClass refClass, boolean capitalizeFirstLetter) {
    if (refClass.isInterface()) {
      return capitalizeFirstLetter
        ? InspectionLocalize.inspectionExportResultsCapitalizedInterface().get()
        : InspectionLocalize.inspectionExportResultsInterface().get();
    } else if (refClass.isAbstract()) {
      return capitalizeFirstLetter
        ? InspectionLocalize.inspectionExportResultsCapitalizedAbstractClass().get()
        : InspectionLocalize.inspectionExportResultsAbstractClass().get();
    } else {
      return capitalizeFirstLetter
        ? InspectionLocalize.inspectionExportResultsCapitalizedClass().get()
        : InspectionLocalize.inspectionExportResultsClass().get();
    }
  }

  public abstract void appendClassExtendsImplements(StringBuffer buf, RefClass refClass);

  public abstract void appendDerivedClasses(StringBuffer buf, RefClass refClass);

  public abstract void appendLibraryMethods(StringBuffer buf, RefClass refClass);

  public abstract void appendSuperMethods(StringBuffer buf, RefMethod refMethod);

  public abstract void appendDerivedMethods(StringBuffer buf, RefMethod refMethod);

  public abstract void appendTypeReferences(StringBuffer buf, RefClass refClass);

  @Override
  public Key<HTMLJavaHTMLComposer> getID() {
    return COMPOSER;
  }

  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}