package com.intellij.java.compiler.impl.javaCompiler;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ConfigurationException;
import consulo.configurable.ProjectConfigurable;
import consulo.java.compiler.JavaCompilerBundle;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.LabeledComponent;
import consulo.ui.ex.awt.ListCellRendererWrapper;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class JavaCompilerConfigurable implements ProjectConfigurable {
  private final JavaCompilerConfiguration myCompilerConfiguration;
  private final Project myProject;
  private JComboBox myComboBox;
  private JCheckBox myNotNullAssertion;
  private TargetOptionsComponent myTargetOptionsComponent;

  @Inject
  public JavaCompilerConfigurable(Project project, JavaCompilerConfiguration javaCompilerConfiguration) {
    myProject = project;
    myCompilerConfiguration = javaCompilerConfiguration;
  }

  @Nonnull
  @Override
  public String getId() {
    return "project.propCompiler.java";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "project.propCompiler";
  }

  @Nonnull
  @Nls
  @Override
  public String getDisplayName() {
    return "Java";
  }

  @RequiredUIAccess
	@Nullable
  @Override
  public JComponent createComponent() {
    JPanel panel = new JPanel(new VerticalFlowLayout());

    myComboBox = new JComboBox();
    myComboBox.setRenderer(new ListCellRendererWrapper<BackendCompiler>() {
      @Override
      public void customize(JList list, BackendCompiler value, int index, boolean selected, boolean hasFocus) {
        setText(value.getPresentableName());
      }
    });

    for (BackendCompiler backendCompiler : myProject.getExtensionPoint(BackendCompiler.class)) {
      myComboBox.addItem(backendCompiler);
    }

    myComboBox.setSelectedItem(myCompilerConfiguration.getActiveCompiler());

    panel.add(LabeledComponent.left(myComboBox, "Compiler"));

    myNotNullAssertion =
      new JCheckBox(JavaCompilerBundle.message("add.notnull.assertions"), myCompilerConfiguration.isAddNotNullAssertions());
    panel.add(myNotNullAssertion);

    myTargetOptionsComponent = new TargetOptionsComponent(myProject);
    panel.add(myTargetOptionsComponent);

    return panel;
  }

  @RequiredUIAccess
	@Override
  public boolean isModified() {
    BackendCompiler item = (BackendCompiler)myComboBox.getSelectedItem();
    if (!Comparing.equal(item, myCompilerConfiguration.getActiveCompiler())) {
      return true;
    }
    if (myNotNullAssertion.isSelected() != myCompilerConfiguration.isAddNotNullAssertions()) {
      return true;
    }
    if (!Comparing.equal(myTargetOptionsComponent.getProjectBytecodeTarget(), myCompilerConfiguration.getProjectBytecodeTarget())) {
      return true;
    }
    if (!Comparing.equal(myTargetOptionsComponent.getModulesBytecodeTargetMap(), myCompilerConfiguration.getModulesBytecodeTargetMap())) {
      return true;
    }
    return false;
  }

  @RequiredUIAccess
	@Override
  public void apply() throws ConfigurationException {
    BackendCompiler ep = (BackendCompiler)myComboBox.getSelectedItem();

    myCompilerConfiguration.setActiveCompiler(ep);
    myCompilerConfiguration.setAddNotNullAssertions(myNotNullAssertion.isSelected());

    myCompilerConfiguration.setProjectBytecodeTarget(myTargetOptionsComponent.getProjectBytecodeTarget());
    myCompilerConfiguration.setModulesBytecodeTargetMap(myTargetOptionsComponent.getModulesBytecodeTargetMap());
  }

  @RequiredUIAccess
	@Override
  public void reset() {
    myComboBox.setSelectedItem(myCompilerConfiguration.getActiveCompiler());
    myNotNullAssertion.setSelected(myCompilerConfiguration.isAddNotNullAssertions());

    myTargetOptionsComponent.setProjectBytecodeTargetLevel(myCompilerConfiguration.getProjectBytecodeTarget());
    myTargetOptionsComponent.setModuleTargetLevels(myCompilerConfiguration.getModulesBytecodeTargetMap());
  }
}
