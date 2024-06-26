/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection.unusedSymbol;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class UnusedSymbolLocalInspectionBase extends BaseJavaLocalInspectionTool {
  @NonNls
  public static final String SHORT_NAME = HighlightInfoType.UNUSED_SYMBOL_SHORT_NAME;
  @NonNls
  public static final String DISPLAY_NAME = HighlightInfoType.UNUSED_SYMBOL_DISPLAY_NAME;
  @NonNls
  public static final String UNUSED_PARAMETERS_SHORT_NAME = "UnusedParameters";

  @NonNls
  public static final String UNUSED_ID = "unused";

  public boolean LOCAL_VARIABLE = true;
  public boolean FIELD = true;
  public boolean METHOD = true;
  public boolean CLASS = true;
  protected boolean INNER_CLASS = true;
  public boolean PARAMETER = true;
  public boolean REPORT_PARAMETER_FOR_PUBLIC_METHODS = true;

  protected String myClassVisibility = PsiModifier.PUBLIC;
  protected String myInnerClassVisibility = PsiModifier.PUBLIC;
  protected String myFieldVisibility = PsiModifier.PUBLIC;
  protected String myMethodVisibility = PsiModifier.PUBLIC;
  protected String myParameterVisibility = PsiModifier.PUBLIC;
  private boolean myIgnoreAccessors = false;

  @PsiModifier.ModifierConstant
  @Nullable
  public String getClassVisibility() {
    if (!CLASS) {
      return null;
    }
    return myClassVisibility;
  }

  @PsiModifier.ModifierConstant
  @Nullable
  public String getFieldVisibility() {
    if (!FIELD) {
      return null;
    }
    return myFieldVisibility;
  }

  @PsiModifier.ModifierConstant
  @Nullable
  public String getMethodVisibility() {
    if (!METHOD) {
      return null;
    }
    return myMethodVisibility;
  }

  @PsiModifier.ModifierConstant
  @Nullable
  public String getParameterVisibility() {
    if (!PARAMETER) {
      return null;
    }
    return myParameterVisibility;
  }

  @PsiModifier.ModifierConstant
  @Nullable
  public String getInnerClassVisibility() {
    if (!INNER_CLASS) {
      return null;
    }
    return myInnerClassVisibility;
  }

  public void setInnerClassVisibility(String innerClassVisibility) {
    myInnerClassVisibility = innerClassVisibility;
  }

  public void setClassVisibility(String classVisibility) {
    this.myClassVisibility = classVisibility;
  }

  public void setFieldVisibility(String fieldVisibility) {
    this.myFieldVisibility = fieldVisibility;
  }

  public void setMethodVisibility(String methodVisibility) {
    this.myMethodVisibility = methodVisibility;
  }

  public void setParameterVisibility(String parameterVisibility) {
    REPORT_PARAMETER_FOR_PUBLIC_METHODS = PsiModifier.PUBLIC.equals(parameterVisibility);
    this.myParameterVisibility = parameterVisibility;
  }

  public boolean isIgnoreAccessors() {
    return myIgnoreAccessors;
  }

  public void setIgnoreAccessors(boolean ignoreAccessors) {
    myIgnoreAccessors = ignoreAccessors;
  }


  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesDeclarationRedundancy().get();
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @Nonnull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @Pattern(VALID_ID_PATTERN)
  @Nonnull
  @NonNls
  public String getID() {
    return UNUSED_ID;
  }

  @Override
  public String getAlternativeID() {
    return UnusedDeclarationInspectionBase.ALTERNATIVE_ID;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Override
  public void writeSettings(@Nonnull Element node) throws WriteExternalException {
    writeVisibility(node, myClassVisibility, "klass");
    writeVisibility(node, myInnerClassVisibility, "inner_class");
    writeVisibility(node, myFieldVisibility, "field");
    writeVisibility(node, myMethodVisibility, "method");
    writeVisibility(node, "parameter", myParameterVisibility, getParameterDefaultVisibility());
    if (myIgnoreAccessors) {
      node.setAttribute("ignoreAccessors", Boolean.toString(true));
    }
    if (!INNER_CLASS) {
      node.setAttribute("INNER_CLASS", Boolean.toString(false));
    }
    super.writeSettings(node);
  }

  private static void writeVisibility(Element node, String visibility, String type) {
    writeVisibility(node, type, visibility, PsiModifier.PUBLIC);
  }

  private static void writeVisibility(Element node, String type, String visibility, String defaultVisibility) {
    if (!defaultVisibility.equals(visibility)) {
      node.setAttribute(type, visibility);
    }
  }

  private String getParameterDefaultVisibility() {
    return REPORT_PARAMETER_FOR_PUBLIC_METHODS ? PsiModifier.PUBLIC : PsiModifier.PRIVATE;
  }

  @Override
  public void readSettings(@Nonnull Element node) throws InvalidDataException {
    super.readSettings(node);
    myClassVisibility = readVisibility(node, "klass");
    myInnerClassVisibility = readVisibility(node, "inner_class");
    myFieldVisibility = readVisibility(node, "field");
    myMethodVisibility = readVisibility(node, "method");
    myParameterVisibility = readVisibility(node, "parameter", getParameterDefaultVisibility());
    final String ignoreAccessors = node.getAttributeValue("ignoreAccessors");
    myIgnoreAccessors = ignoreAccessors != null && Boolean.parseBoolean(ignoreAccessors);
    final String innerClassEnabled = node.getAttributeValue("INNER_CLASS");
    INNER_CLASS = innerClassEnabled == null || Boolean.parseBoolean(innerClassEnabled);
  }

  private static String readVisibility(@Nonnull Element node, final String type) {
    return readVisibility(node, type, PsiModifier.PUBLIC);
  }

  private static String readVisibility(@Nonnull Element node, final String type, final String defaultVisibility) {
    final String visibility = node.getAttributeValue(type);
    if (visibility == null) {
      return defaultVisibility;
    }
    return visibility;
  }
}
