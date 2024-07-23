/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizer;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.TreeSet;

@ExtensionImpl
public class CollectionsMustHaveInitialCapacityInspection extends BaseInspection {
  private final CollectionsListSettings mySettings = new CollectionsListSettings() {
    @Override
    protected Set<String> getDefaultSettings() {
      final Set<String> classes = new TreeSet<>(DEFAULT_COLLECTION_LIST);
      classes.add("java.util.BitSet");
      return classes;
    }
  };
  public boolean myIgnoreFields;

  @Override
  public void readSettings(@Nonnull Element node) throws InvalidDataException {
    mySettings.readSettings(node);
    myIgnoreFields = JDOMExternalizer.readBoolean(node, "ignoreFields");
  }

  @Override
  public void writeSettings(@Nonnull Element node) throws WriteExternalException {
    mySettings.writeSettings(node);
    if (myIgnoreFields) {
      JDOMExternalizer.write(node, "ignoreFields", true);
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    JPanel p = new JPanel(new BorderLayout());
    p.add(new SingleCheckboxOptionsPanel("don't report field's initializers", this, "myIgnoreFields"), BorderLayout.NORTH);
    p.add(mySettings.createOptionsPanel(), BorderLayout.CENTER);
    return p;
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @Nonnull
  public String getID() {
    return "CollectionWithoutInitialCapacity";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.collectionsMustHaveInitialCapacityDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.collectionsMustHaveInitialCapacityProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CollectionInitialCapacityVisitor();
  }

  private class CollectionInitialCapacityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@Nonnull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (myIgnoreFields && expression.getParent() instanceof PsiField) {
        return;
      }

      final PsiType type = expression.getType();
      if (!isCollectionWithInitialCapacity(type)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null || argumentList.getExpressions().length != 0) {
        return;
      }
      registerNewExpressionError(expression);
    }

    private boolean isCollectionWithInitialCapacity(@Nullable PsiType type) {
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType) type;
      final PsiClass resolved = classType.resolve();
      if (resolved == null) {
        return false;
      }
      final String className = resolved.getQualifiedName();
      return mySettings.getCollectionClassesRequiringCapacity().contains(className);
    }
  }
}