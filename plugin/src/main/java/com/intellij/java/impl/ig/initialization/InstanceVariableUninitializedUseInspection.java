/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.initialization;

import com.intellij.java.impl.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.impl.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.intellij.java.impl.ig.psiutils.UninitializedReadCollector;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.component.extension.Extensions;
import consulo.language.editor.ImplicitUsageProvider;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class InstanceVariableUninitializedUseInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignorePrimitives = false;

  /**
   * @noinspection PublicField
   */
  @NonNls
  public String annotationNamesString = "";
  private final List<String> annotationNames = new ArrayList();

  public InstanceVariableUninitializedUseInspection() {
    parseString(annotationNamesString, annotationNames);
  }

  @Override
  @Nonnull
  public String getID() {
    return "InstanceVariableUsedBeforeInitialized";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("instance.variable.used.before.initialized.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("instance.variable.used.before.initialized.problem.descriptor");
  }

  @Override
  public void readSettings(@Nonnull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(annotationNamesString, annotationNames);
  }

  @Override
  public void writeSettings(@Nonnull Element element) throws WriteExternalException {
    annotationNamesString = formatString(annotationNames);
    super.writeSettings(element);
  }

  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());

    final JPanel annotationsPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      annotationNames, InspectionGadgetsBundle.message("ignore.if.annotated.by"));
    final consulo.deadCodeNotWorking.impl.CheckBox checkBox = new consulo.deadCodeNotWorking.impl.CheckBox(InspectionGadgetsBundle.message("primitive.fields.ignore.option"), this, "m_ignorePrimitives");

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(annotationsPanel, constraints);

    constraints.gridy = 1;
    constraints.weighty = 0.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(checkBox, constraints);

    return panel;
  }

  @Nonnull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return AddToIgnoreIfAnnotatedByListQuickFix.build(field, annotationNames);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstanceVariableInitializationVisitor();
  }

  private class InstanceVariableInitializationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (field.getInitializer() != null) {
        return;
      }
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(field, annotationNames);
      if (annotation != null) {
        return;
      }
      if (m_ignorePrimitives) {
        final PsiType fieldType = field.getType();
        if (ClassUtils.isPrimitive(fieldType)) {
          return;
        }
      }
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return;
      }
      for (ImplicitUsageProvider provider :
        Extensions.getExtensions(ImplicitUsageProvider.EP_NAME)) {
        if (provider.isImplicitWrite(field)) {
          return;
        }
      }
      final UninitializedReadCollector uninitializedReadsCollector = new UninitializedReadCollector();
      if (!isInitializedInInitializer(field, uninitializedReadsCollector)) {
        final PsiMethod[] constructors = aClass.getConstructors();
        for (final PsiMethod constructor : constructors) {
          final PsiCodeBlock body = constructor.getBody();
          uninitializedReadsCollector.blockAssignsVariable(body, field);
        }
      }
      final PsiExpression[] badReads = uninitializedReadsCollector.getUninitializedReads();
      for (PsiExpression expression : badReads) {
        registerError(expression, field);
      }
    }

    private boolean isInitializedInInitializer(@Nonnull PsiField field, UninitializedReadCollector uninitializedReadsCollector) {
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      for (final PsiClassInitializer initializer : initializers) {
        if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiCodeBlock body = initializer.getBody();
          if (uninitializedReadsCollector.blockAssignsVariable(body, field)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}