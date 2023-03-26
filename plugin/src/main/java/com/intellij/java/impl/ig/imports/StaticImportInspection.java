/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.imports;

import com.intellij.java.impl.ig.fixes.IgnoreClassFix;
import com.intellij.java.impl.ig.fixes.SuppressForTestsScopeFix;
import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiImportStaticStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.List;

@ExtensionImpl
public class StaticImportInspection extends StaticImportInspectionBase {

  @Nonnull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> result = new SmartList<>();
    final PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement) infos[0];
    final SuppressForTestsScopeFix fix = SuppressForTestsScopeFix.build(this, importStaticStatement);
    ContainerUtil.addIfNotNull(result, fix);
    final PsiClass aClass = importStaticStatement.resolveTargetClass();
    if (aClass != null) {
      final String name = aClass.getQualifiedName();
      result.add(new IgnoreClassFix(name, allowedClasses, "Allow static imports for class '" + name + "'"));
    }
    result.add(buildFix(infos));
    return result.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    final JPanel chooserList = UiUtils.createTreeClassChooserList(allowedClasses, "Statically importable Classes", "Choose statically importable class");
    panel.add(chooserList, constraints);

    constraints.gridy = 1;
    constraints.weighty = 0.0;
    final consulo.deadCodeNotWorking.impl.CheckBox checkBox1 = new consulo.deadCodeNotWorking.impl.CheckBox(InspectionGadgetsBundle.message("ignore.single.field.static.imports.option"), this, "ignoreSingleFieldImports");
    panel.add(checkBox1, constraints);

    constraints.gridy = 2;
    final consulo.deadCodeNotWorking.impl.CheckBox checkBox2 = new consulo.deadCodeNotWorking.impl.CheckBox(InspectionGadgetsBundle.message("ignore.single.method.static.imports.option"), this, "ignoreSingeMethodImports");
    panel.add(checkBox2, constraints);

    return panel;
  }
}
