/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.naming;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.codeInspection.ui.ListTable;
import consulo.ide.impl.idea.codeInspection.ui.ListWrappingTableModel;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiVariable;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.impl.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ExtensionImpl
public class QuestionableNameInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  @NonNls public String nameString = "aa,abc,bad,bar,bar2,baz,baz1,baz2," +
                                     "baz3,bb,blah,bogus,bool,cc,dd,defau1t,dummy,dummy2,ee,fa1se," +
                                     "ff,foo,foo1,foo2,foo3,foobar,four,fred,fred1,fred2,gg,hh,hello," +
                                     "hello1,hello2,hello3,ii,nu11,one,silly,silly2,string,two,that," +
                                     "then,three,whi1e,var";

  List<String> nameList = new ArrayList<String>(32);

  public QuestionableNameInspection() {
    parseString(nameString, nameList);
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "questionable.name.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "questionable.name.problem.descriptor");
  }

  @Override
  public void readSettings(@Nonnull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(nameString, nameList);
  }

  @Override
  public void writeSettings(@Nonnull Element element) throws WriteExternalException {
    nameString = formatString(nameList);
    super.writeSettings(element);
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table =
      new ListTable(new ListWrappingTableModel(nameList, InspectionGadgetsBundle.message("questionable.name.column.title")));
    return UiUtils.createAddRemovePanel(table);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new QuestionableNameVisitor();
  }

  private class QuestionableNameVisitor extends BaseInspectionVisitor {

    private final Set<String> nameSet = new HashSet(nameList);

    @Override
    public void visitVariable(@Nonnull PsiVariable variable) {
      final String name = variable.getName();
      if (nameSet.contains(name)) {
        registerVariableError(variable);
      }
    }

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      final String name = method.getName();
      if (nameSet.contains(name)) {
        registerMethodError(method);
      }
    }

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      final String name = aClass.getName();
      if (nameSet.contains(name)) {
        registerClassError(aClass);
      }
    }
  }
}