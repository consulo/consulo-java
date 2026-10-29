package com.intellij.java.compiler.impl.javaCompiler;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ConfigurationException;
import consulo.configurable.ProjectConfigurable;
import consulo.disposer.Disposable;
import consulo.java.compiler.JavaCompilerBundle;
import consulo.java.compiler.localize.JavaCompilerLocalize;
import consulo.java.language.localize.JavaLanguageLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.LabeledComponent;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.Comparing;
import org.jspecify.annotations.Nullable;
import jakarta.inject.Inject;

import javax.swing.*;

@ExtensionImpl
public class JavaCompilerConfigurable implements ProjectConfigurable {
    private final JavaCompilerConfiguration myCompilerConfiguration;
    private final Project myProject;
    private JComboBox<BackendCompiler> myComboBox;
    private JCheckBox myNotNullAssertion;

    @Inject
    public JavaCompilerConfigurable(Project project, JavaCompilerConfiguration javaCompilerConfiguration) {
        myProject = project;
        myCompilerConfiguration = javaCompilerConfiguration;
    }

    @Override
    public String getId() {
        return "project.propCompiler.java";
    }

    @Nullable
    @Override
    public String getParentId() {
        return "project.propCompiler";
    }

    @Override
    public LocalizeValue getDisplayName() {
        return JavaLanguageLocalize.javaLanguageDisplayName();
    }

    @RequiredUIAccess
    @Nullable
    @Override
    public Component createUIComponent(Disposable parentDisposable) {
        JPanel panel = new JPanel(new VerticalFlowLayout());

        myComboBox = new JComboBox<>();
        myComboBox.setRenderer(new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(JList jList, BackendCompiler o, int i, boolean b, boolean b1) {
                if (o != null) {
                    append(o.getPresentableName());
                }
            }
        });

        for (BackendCompiler backendCompiler : myProject.getExtensionPoint(BackendCompiler.class)) {
            myComboBox.addItem(backendCompiler);
        }

        myComboBox.setSelectedItem(myCompilerConfiguration.getActiveCompiler());

        panel.add(LabeledComponent.left(myComboBox, "Compiler"));

        myNotNullAssertion =
            new JCheckBox(JavaCompilerLocalize.addNotnullAssertions().get(), myCompilerConfiguration.isAddNotNullAssertions());
        panel.add(myNotNullAssertion);

        return TargetAWT.wrap(panel);
    }

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        BackendCompiler item = (BackendCompiler) myComboBox.getSelectedItem();
        if (!Comparing.equal(item, myCompilerConfiguration.getActiveCompiler())) {
            return true;
        }
        if (myNotNullAssertion.isSelected() != myCompilerConfiguration.isAddNotNullAssertions()) {
            return true;
        }
        return false;
    }

    @RequiredUIAccess
    @Override
    public void apply() throws ConfigurationException {
        BackendCompiler ep = (BackendCompiler) myComboBox.getSelectedItem();

        myCompilerConfiguration.setActiveCompiler(ep);
        myCompilerConfiguration.setAddNotNullAssertions(myNotNullAssertion.isSelected());
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        myComboBox.setSelectedItem(myCompilerConfiguration.getActiveCompiler());
        myNotNullAssertion.setSelected(myCompilerConfiguration.isAddNotNullAssertions());
    }
}
