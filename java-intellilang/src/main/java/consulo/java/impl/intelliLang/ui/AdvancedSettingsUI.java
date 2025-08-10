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

package consulo.java.impl.intelliLang.ui;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.util.ClassFilter;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.annotation.access.RequiredReadAction;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.document.Document;
import consulo.java.impl.intelliLang.util.PsiUtilEx;
import consulo.language.editor.ui.awt.ReferenceEditorWithBrowseButton;
import consulo.language.inject.advanced.Configuration;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.function.Function;

/**
 * @author Gregory.Shrago
 */
public class AdvancedSettingsUI implements SearchableConfigurable {
  private final Configuration.AdvancedConfiguration myConfiguration;
  private AdvancedSettingsPanel myPanel;
  private final Project myProject;

  public AdvancedSettingsUI(@Nonnull final Project project, Configuration configuration) {
    myProject = project;
    myConfiguration = configuration.getAdvancedConfiguration();
  }

  @Override
  @RequiredUIAccess
  public JComponent createComponent() {
    myPanel = new AdvancedSettingsPanel();
    return myPanel.myRoot;
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  @Override
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Advanced");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.injection.advanced";
  }

  @Nonnull
  @Override
  public String getId() {
    return "IntelliLang.Advanced";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  private static class BrowseClassListener implements ActionListener {
    private final Project myProject;
    private final ReferenceEditorWithBrowseButton myField;

    private BrowseClassListener(Project project, ReferenceEditorWithBrowseButton annotationField) {
      myProject = project;
      myField = annotationField;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);

      final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      final PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(myField.getText(), scope);
      final TreeClassChooser chooser = factory.createNoInnerClassesScopeChooser("Select Annotation Class", scope, new ClassFilter() {
        @Override
        public boolean isAccepted(PsiClass aClass) {
          return aClass.isAnnotationType();
        }
      }, aClass);

      chooser.showDialog();
      final PsiClass psiClass = chooser.getSelected();
      if (psiClass != null) {
        myField.setText(psiClass.getQualifiedName());
      }
    }
  }

  public class AdvancedSettingsPanel {
    @SuppressWarnings({
        "UnusedDeclaration",
        "FieldCanBeLocal"
    })
    private JPanel myRoot;

    private JRadioButton myNoInstrumentation;
    private JRadioButton myAssertInstrumentation;
    private JRadioButton myExceptionInstrumentation;
    private JPanel myLanguageAnnotationPanel;
    private JPanel myPatternAnnotationPanel;
    private JPanel mySubstAnnotationPanel;
    private JRadioButton myDfaOff;
    private JRadioButton myAnalyzeReferences;
    private JRadioButton myUseDfa;
    private JRadioButton myLookForAssignments;
    private JCheckBox myIncludeUncomputableOperandsAsCheckBox;
    private JCheckBox mySourceModificationAllowedCheckBox;

    private final ReferenceEditorWithBrowseButton myAnnotationField;
    private final ReferenceEditorWithBrowseButton myPatternField;
    private final ReferenceEditorWithBrowseButton mySubstField;

    @RequiredReadAction
    public AdvancedSettingsPanel() {
      myAnnotationField = new ReferenceEditorWithBrowseButton(null, myProject, new Function<String, Document>() {
        @Override
        @RequiredReadAction
        public Document apply(String s) {
          return PsiUtilEx.createDocument(s, myProject);
        }
      }, myConfiguration.getLanguageAnnotationClass());
      myAnnotationField.addActionListener(new BrowseClassListener(myProject, myAnnotationField));
      myAnnotationField.setEnabled(!myProject.isDefault());
      addField(myLanguageAnnotationPanel, myAnnotationField);

      myPatternField = new ReferenceEditorWithBrowseButton(null, myProject, new Function<String, Document>() {
        @Override
        @RequiredReadAction
        public Document apply(String s) {
          return PsiUtilEx.createDocument(s, myProject);
        }
      }, myConfiguration.getPatternAnnotationClass());
      myPatternField.addActionListener(new BrowseClassListener(myProject, myPatternField));
      myPatternField.setEnabled(!myProject.isDefault());
      addField(myPatternAnnotationPanel, myPatternField);

      mySubstField = new ReferenceEditorWithBrowseButton(null, myProject, new Function<String, Document>() {
        @Override
        @RequiredReadAction
        public Document apply(String s) {
          return PsiUtilEx.createDocument(s, myProject);
        }
      }, myConfiguration.getPatternAnnotationClass());
      mySubstField.addActionListener(new BrowseClassListener(myProject, mySubstField));
      mySubstField.setEnabled(!myProject.isDefault());
      addField(mySubstAnnotationPanel, mySubstField);
    }
    //

    /**
     * Adds textfield into placeholder panel and assigns a directly preceding label
     */
    private void addField(JPanel panel, ReferenceEditorWithBrowseButton field) {
      panel.add(field, BorderLayout.CENTER);

      final Component[] components = panel.getParent().getComponents();
      final int index = Arrays.asList(components).indexOf(panel);
      if (index > 0) {
        final Component component = components[index - 1];
        if (component instanceof JLabel) {
          ((JLabel) component).setLabelFor(field);
        }
      }
    }


    @SuppressWarnings({"SimplifiableIfStatement"})
    public boolean isModified() {
      if (getInstrumentation() != myConfiguration.getInstrumentation()) {
        return true;
      }
      if (!myAnnotationField.getText().equals(myConfiguration.getLanguageAnnotationClass())) {
        return true;
      }
      if (!myPatternField.getText().equals(myConfiguration.getPatternAnnotationClass())) {
        return true;
      }
      if (!mySubstField.getText().equals(myConfiguration.getSubstAnnotationClass())) {
        return true;
      }
      if (!myConfiguration.getDfaOption().equals(getDfaOption())) {
        return true;
      }
      if (myConfiguration.isIncludeUncomputablesAsLiterals() != myIncludeUncomputableOperandsAsCheckBox.isSelected()) {
        return true;
      }
      if (myConfiguration.isSourceModificationAllowed() != mySourceModificationAllowedCheckBox.isSelected()) {
        return true;
      }
      return false;
    }

    @Nonnull
    private Configuration.InstrumentationType getInstrumentation() {
      if (myNoInstrumentation.isSelected()) {
        return Configuration.InstrumentationType.NONE;
      }
      if (myAssertInstrumentation.isSelected()) {
        return Configuration.InstrumentationType.ASSERT;
      }
      if (myExceptionInstrumentation.isSelected()) {
        return Configuration.InstrumentationType.EXCEPTION;
      }

      assert false;
      return null;
    }

    public void apply() throws ConfigurationException {
      myConfiguration.setInstrumentationType(getInstrumentation());
      myConfiguration.setLanguageAnnotation(myAnnotationField.getText());
      myConfiguration.setPatternAnnotation(myPatternField.getText());
      myConfiguration.setSubstAnnotation(mySubstField.getText());

      myConfiguration.setDfaOption(getDfaOption());
      myConfiguration.setIncludeUncomputablesAsLiterals(myIncludeUncomputableOperandsAsCheckBox.isSelected());
      myConfiguration.setSourceModificationAllowed(mySourceModificationAllowedCheckBox.isSelected());
    }

    @Nonnull
    private Configuration.DfaOption getDfaOption() {
      if (myDfaOff.isSelected()) {
        return Configuration.DfaOption.OFF;
      }
      if (myAnalyzeReferences.isSelected()) {
        return Configuration.DfaOption.RESOLVE;
      }
      if (myLookForAssignments.isSelected()) {
        return Configuration.DfaOption.ASSIGNMENTS;
      }
      if (myUseDfa.isSelected()) {
        return Configuration.DfaOption.DFA;
      }
      return Configuration.DfaOption.OFF;
    }

    public void reset() {
      myAnnotationField.setText(myConfiguration.getLanguageAnnotationClass());
      myPatternField.setText(myConfiguration.getPatternAnnotationClass());
      mySubstField.setText(myConfiguration.getSubstAnnotationClass());

      myNoInstrumentation.setSelected(myConfiguration.getInstrumentation() == Configuration.InstrumentationType.NONE);
      myAssertInstrumentation.setSelected(myConfiguration.getInstrumentation() == Configuration.InstrumentationType.ASSERT);
      myExceptionInstrumentation.setSelected(myConfiguration.getInstrumentation() == Configuration.InstrumentationType.EXCEPTION);

      setDfaOption(myConfiguration.getDfaOption());
      myIncludeUncomputableOperandsAsCheckBox.setSelected(myConfiguration.isIncludeUncomputablesAsLiterals());
      mySourceModificationAllowedCheckBox.setSelected(myConfiguration.isSourceModificationAllowed());
    }

    private void setDfaOption(@Nonnull final Configuration.DfaOption dfaOption) {
      switch (dfaOption) {
        case OFF:
          myDfaOff.setSelected(true);
          break;
        case RESOLVE:
          myAnalyzeReferences.setSelected(true);
          break;
        case ASSIGNMENTS:
          myLookForAssignments.setSelected(true);
          break;
        case DFA:
          myUseDfa.setSelected(true);
          break;
      }
    }
  }
}
