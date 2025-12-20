/*
 * Copyright 2007-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.serialization;

import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class SerializableInspection extends BaseInspection {

  private static final JComponent[] EMPTY_COMPONENT_ARRAY = {};

  @SuppressWarnings({"PublicField"})
  public boolean ignoreAnonymousInnerClasses = false;

  @Deprecated @SuppressWarnings({"PublicField"})
  public String superClassString = "java.awt.Component";
  protected List<String> superClassList = new ArrayList();

  @Override
  public final JComponent createOptionsPanel() {
    JComponent panel = new JPanel(new GridBagLayout());

    JPanel chooserList = UiUtils.createTreeClassChooserList(
      superClassList,
      InspectionGadgetsLocalize.ignoreClassesInHierarchyColumnName().get(),
      InspectionGadgetsLocalize.chooseSuperClassToIgnore().get()
    );
    UiUtils.setComponentSize(chooserList, 7, 25);
    CheckBox checkBox = new CheckBox(
      InspectionGadgetsLocalize.ignoreAnonymousInnerClasses().get(),
      this,
      "ignoreAnonymousInnerClasses"
    );

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;

    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(chooserList, constraints);

    constraints.fill = GridBagConstraints.BOTH;
    JComponent[] additionalOptions = createAdditionalOptions();
    for (JComponent additionalOption : additionalOptions) {
      constraints.gridy++;
      panel.add(additionalOption, constraints);
    }

    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridy++;
    constraints.weighty = 0.0;
    panel.add(checkBox, constraints);
    return panel;
  }

  @Override
  public void readSettings(@Nonnull Element node) throws InvalidDataException {
    super.readSettings(node);
    parseString(superClassString, superClassList);
  }

  @Override
  public void writeSettings(@Nonnull Element node) throws WriteExternalException {
    superClassString = formatString(superClassList);
    super.writeSettings(node);
  }

  protected JComponent[] createAdditionalOptions() {
    return EMPTY_COMPONENT_ARRAY;
  }

  protected boolean isIgnoredSubclass(PsiClass aClass) {
    if (SerializationUtils.isDirectlySerializable(aClass)) {
      return false;
    }
    for (String superClassName : superClassList) {
      if (InheritanceUtil.isInheritor(aClass, superClassName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getAlternativeID() {
    return "serial";
  }
}