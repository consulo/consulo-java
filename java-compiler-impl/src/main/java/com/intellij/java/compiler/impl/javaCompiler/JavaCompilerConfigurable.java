package com.intellij.java.compiler.impl.javaCompiler;

import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.java.compiler.JavaCompilerBundle;
import consulo.project.Project;
import consulo.ui.ex.awt.LabeledComponent;
import consulo.ui.ex.awt.ListCellRendererWrapper;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.util.lang.Comparing;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nullable;
import javax.swing.*;

public class JavaCompilerConfigurable implements Configurable
{
	private final JavaCompilerConfiguration myCompilerConfiguration;
	private final Project myProject;
	private JComboBox myComboBox;
	private JCheckBox myNotNullAssertion;
	private TargetOptionsComponent myTargetOptionsComponent;

	public JavaCompilerConfigurable(Project project)
	{
		myProject = project;
		myCompilerConfiguration = JavaCompilerConfiguration.getInstance(project);
	}

	@Nls
	@Override
	public String getDisplayName()
	{
		return "Java";
	}

	@Nullable
	@Override
	public String getHelpTopic()
	{
		return null;
	}

	@Nullable
	@Override
	public JComponent createComponent()
	{
		JPanel panel = new JPanel(new VerticalFlowLayout());

		myComboBox = new JComboBox();
		myComboBox.setRenderer(new ListCellRendererWrapper<BackendCompiler>()
		{
			@Override
			public void customize(JList list, BackendCompiler value, int index, boolean selected, boolean hasFocus)
			{
				setText(value.getPresentableName());
			}
		});

		for(BackendCompiler backendCompiler : BackendCompiler.EP_NAME.getExtensionList(myProject))
		{
			myComboBox.addItem(backendCompiler);
		}

		myComboBox.setSelectedItem(myCompilerConfiguration.getActiveCompiler());

		panel.add(LabeledComponent.left(myComboBox, "Compiler"));

		myNotNullAssertion = new JCheckBox(JavaCompilerBundle.message("add.notnull.assertions"), myCompilerConfiguration.isAddNotNullAssertions());
		panel.add(myNotNullAssertion);

		myTargetOptionsComponent = new TargetOptionsComponent(myProject);
		panel.add(myTargetOptionsComponent);

		return panel;
	}

	@Override
	public boolean isModified()
	{
		BackendCompiler item = (BackendCompiler) myComboBox.getSelectedItem();
		if(!Comparing.equal(item, myCompilerConfiguration.getActiveCompiler()))
		{
			return true;
		}
		if(myNotNullAssertion.isSelected() != myCompilerConfiguration.isAddNotNullAssertions())
		{
			return true;
		}
		if(!Comparing.equal(myTargetOptionsComponent.getProjectBytecodeTarget(), myCompilerConfiguration.getProjectBytecodeTarget()))
		{
			return true;
		}
		if(!Comparing.equal(myTargetOptionsComponent.getModulesBytecodeTargetMap(), myCompilerConfiguration.getModulesBytecodeTargetMap()))
		{
			return true;
		}
		return false;
	}

	@Override
	public void apply() throws ConfigurationException
	{
		BackendCompiler ep = (BackendCompiler) myComboBox.getSelectedItem();

		myCompilerConfiguration.setActiveCompiler(ep);
		myCompilerConfiguration.setAddNotNullAssertions(myNotNullAssertion.isSelected());

		myCompilerConfiguration.setProjectBytecodeTarget(myTargetOptionsComponent.getProjectBytecodeTarget());
		myCompilerConfiguration.setModulesBytecodeTargetMap(myTargetOptionsComponent.getModulesBytecodeTargetMap());
	}

	@Override
	public void reset()
	{
		myComboBox.setSelectedItem(myCompilerConfiguration.getActiveCompiler());
		myNotNullAssertion.setSelected(myCompilerConfiguration.isAddNotNullAssertions());

		myTargetOptionsComponent.setProjectBytecodeTargetLevel(myCompilerConfiguration.getProjectBytecodeTarget());
		myTargetOptionsComponent.setModuleTargetLevels(myCompilerConfiguration.getModulesBytecodeTargetMap());
	}

	@Override
	public void disposeUIResources()
	{

	}
}
