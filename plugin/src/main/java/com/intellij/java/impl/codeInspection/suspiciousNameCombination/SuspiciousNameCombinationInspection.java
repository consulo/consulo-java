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

package com.intellij.java.impl.codeInspection.suspiciousNameCombination;

import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.matcher.NameUtil;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ui.ex.awt.AddEditDeleteListPanel;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
@ExtensionImpl
public class SuspiciousNameCombinationInspection extends BaseLocalInspectionTool {
  private final List<String> myNameGroups = new ArrayList<String>();
  private final Map<String, String> myWordToGroupMap = new HashMap<String, String>();
  @NonNls private static final String ELEMENT_GROUPS = "group";
  @NonNls private static final String ATTRIBUTE_NAMES = "names";

  public SuspiciousNameCombinationInspection() {
    addNameGroup("x,width,left,right");
    addNameGroup("y,height,top,bottom");
  }

  private void clearNameGroups() {
    myNameGroups.clear();
    myWordToGroupMap.clear();
  }

  private void addNameGroup(@NonNls final String group) {
    myNameGroups.add(group);
    List<String> words = StringUtil.split(group, ",");
    for(String word: words) {
      myWordToGroupMap.put(word.trim().toLowerCase(), group);
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("suspicious.name.combination.display.name");
  }

  @Override
  @Nonnull
  @NonNls
  public String getShortName() {
    return "SuspiciousNameCombination";
  }

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(@Nonnull ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new MyVisitor(holder);
  }

  @Override @Nullable
  public JComponent createOptionsPanel() {
    return new MyOptionsPanel();
  }

  @Override public void readSettings(@Nonnull Element node) throws InvalidDataException {
    clearNameGroups();
    for(Object o: node.getChildren(ELEMENT_GROUPS)) {
      Element e = (Element) o;
      addNameGroup(e.getAttributeValue(ATTRIBUTE_NAMES));
    }
  }

  @Override public void writeSettings(@Nonnull Element node) throws WriteExternalException {
    for(String group: myNameGroups) {
      Element e = new Element(ELEMENT_GROUPS);
      node.addContent(e);
      e.setAttribute(ATTRIBUTE_NAMES, group);
    }
  }

  private class MyVisitor extends JavaElementVisitor {
    private final ProblemsHolder myProblemsHolder;

    public MyVisitor(final ProblemsHolder problemsHolder) {
      myProblemsHolder = problemsHolder;
    }
    @Override public void visitVariable(PsiVariable variable) {
      if (variable.hasInitializer()) {
        PsiExpression expr = variable.getInitializer();
        if (expr instanceof PsiReferenceExpression) {
          PsiReferenceExpression refExpr = (PsiReferenceExpression) expr;
          checkCombination(variable, variable.getName(), refExpr.getReferenceName(), "suspicious.name.assignment");
        }
      }
    }

    @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      PsiExpression lhs = expression.getLExpression();
      PsiExpression rhs = expression.getRExpression();
      if (lhs instanceof PsiReferenceExpression && rhs instanceof PsiReferenceExpression) {
        PsiReferenceExpression lhsExpr = (PsiReferenceExpression) lhs;
        PsiReferenceExpression rhsExpr = (PsiReferenceExpression) rhs;
        checkCombination(lhsExpr, lhsExpr.getReferenceName(), rhsExpr.getReferenceName(), "suspicious.name.assignment");
      }
    }

    @Override public void visitCallExpression(PsiCallExpression expression) {
      final PsiMethod psiMethod = expression.resolveMethod();
      final PsiExpressionList argList = expression.getArgumentList();
      if (psiMethod != null && argList != null) {
        final PsiExpression[] args = argList.getExpressions();
        final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
        for(int i=0; i<parameters.length; i++) {
          if (i >= args.length) break;
          if (args [i] instanceof PsiReferenceExpression) {
            // PsiParameter.getName() can be expensive for compiled class files, so check reference name before
            // fetching parameter name
            final String refName = ((PsiReferenceExpression)args[i]).getReferenceName();
            if (findNameGroup(refName) != null) {
              checkCombination(args [i], parameters [i].getName(), refName, "suspicious.name.parameter");
            }
          }
        }
      }
    }

    @Override
    public void visitReturnStatement(final PsiReturnStatement statement) {
      final PsiExpression returnValue = statement.getReturnValue();
      PsiMethod containingMethod = PsiTreeUtil.getParentOfType(returnValue, PsiMethod.class);
      if (returnValue instanceof PsiReferenceExpression && containingMethod != null) {
        final String refName = ((PsiReferenceExpression)returnValue).getReferenceName();
        checkCombination(returnValue, containingMethod.getName(), refName, "suspicious.name.return");
      }
    }

    private void checkCombination(final PsiElement location,
                                  @Nullable final String name,
                                  @Nullable final String referenceName,
                                  final String key) {
      String nameGroup1 = findNameGroup(name);
      String nameGroup2 = findNameGroup(referenceName);
      if (nameGroup1 != null && nameGroup2 != null && !nameGroup1.equals(nameGroup2)) {
        myProblemsHolder.registerProblem(location, JavaErrorBundle.message(key, referenceName, name));
      }
    }

    @Nullable
	private String findNameGroup(@Nullable final String name) {
      if (name == null) {
        return null;
      }
      String[] words = NameUtil.splitNameIntoWords(name);
      String result = null;
      for(String word: words) {
        String group = myWordToGroupMap.get(word.toLowerCase());
        if (group != null) {
          if (result == null) {
            result = group;
          }
          else if (!result.equals(group)) {
            result = null;
            break;
          }
        }
      }
      return result;
    }
  }

  private class MyOptionsPanel extends AddEditDeleteListPanel<String> {

    public MyOptionsPanel() {
      super(InspectionsBundle.message("suspicious.name.combination.options.title"), myNameGroups);
      myListModel.addListDataListener(new ListDataListener() {
        @Override
        public void intervalAdded(ListDataEvent e) {
          saveChanges();
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
          saveChanges();
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
          saveChanges();
        }
      });
    }

    @Override
    protected String findItemToAdd() {
      return Messages.showInputDialog(this,
                                      InspectionsBundle.message("suspicious.name.combination.options.prompt"),
                                      InspectionsBundle.message("suspicious.name.combination.add.titile"),
                                      Messages.getQuestionIcon(), "", null);
    }

    @Override
    protected String editSelectedItem(String inputValue) {
      return Messages.showInputDialog(this,
                                      InspectionsBundle.message("suspicious.name.combination.options.prompt"),
                                      InspectionsBundle.message("suspicious.name.combination.edit.title"),
                                      Messages.getQuestionIcon(),
                                      inputValue, null);
    }

    private void saveChanges() {
      clearNameGroups();
      for(int i=0; i<myListModel.getSize(); i++) {
        addNameGroup((String) myListModel.getElementAt(i));
      }
    }
  }
}
