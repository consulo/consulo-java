/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.application;

import com.intellij.java.execution.configurations.ConfigurationUtil;
import com.intellij.java.execution.impl.ui.ClassBrowser;
import com.intellij.java.execution.impl.ui.CommonJavaParametersLayout;
import com.intellij.java.execution.impl.ui.UnifiedConfigurationModuleSelector;
import com.intellij.java.execution.impl.ui.UnifiedJrePathEditor;
import com.intellij.java.execution.impl.ui.UnifiedShortenCommandLineModeCombo;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.ui.JavaReferenceEditorUtil;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import consulo.application.concurrent.coroutine.ReadLock;
import consulo.configurable.ConfigurationException;
import consulo.document.Document;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.language.editor.ui.EditorBox;
import consulo.language.editor.ui.EditorBoxBuilderFactory;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.UIAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.ActionToolbarFactory;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.util.FormBuilder;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.CoroutineScope;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public class ApplicationConfigurable extends SettingsEditor<ApplicationConfiguration> {
    private final Project myProject;

    private @Nullable ApplicationParametersLayout myLayout;

    public ApplicationConfigurable(Project project) {
        myProject = project;
    }

    @Override
    @RequiredUIAccess
    protected Component createUIComponent() {
        ApplicationParametersLayout layout = new ApplicationParametersLayout();
        layout.build();
        myLayout = layout;
        return layout.getComponent();
    }

    @Override
    @RequiredUIAccess
    protected void resetEditorFrom(ApplicationConfiguration configuration) {
        ApplicationParametersLayout layout = myLayout;
        if (layout != null) {
            layout.reset(configuration);
        }
    }

    @Override
    @RequiredUIAccess
    protected void applyEditorTo(ApplicationConfiguration configuration) throws ConfigurationException {
        ApplicationParametersLayout layout = myLayout;
        if (layout != null) {
            layout.apply(configuration);
        }
    }

    private class ApplicationParametersLayout extends CommonJavaParametersLayout<ApplicationConfiguration> {
        private final UnifiedConfigurationModuleSelector myModuleSelector;
        private final EditorBox myMainClassField;
        private final UnifiedJrePathEditor myJrePathEditor;
        private final UnifiedShortenCommandLineModeCombo myShortenCommandLineModeCombo;
        private final CheckBox myIncludeProviderDepsBox;

        @RequiredUIAccess
        private ApplicationParametersLayout() {
            super(myProject.getApplication().getInstance(DialogService.class));

            myModuleSelector = new UnifiedConfigurationModuleSelector(myProject, JavaExecutionLocalize.runConfigurationModuleNone());
            myModuleSelector.addValueListener(this::setModuleContext);
            myMainClassField = createMainClassField();
            myJrePathEditor = new UnifiedJrePathEditor(ApplicationConfigurable.this);
            myShortenCommandLineModeCombo = new UnifiedShortenCommandLineModeCombo(myProject, myJrePathEditor, myModuleSelector);
            myIncludeProviderDepsBox = CheckBox.create(JavaExecutionLocalize.applicationConfigurationIncludeProvidedScope());
        }

        @RequiredUIAccess
        private EditorBox createMainClassField() {
            EditorBox mainClassField = myProject.getApplication().getInstance(EditorBoxBuilderFactory.class).create(myProject).build();
            if (myProject.isDefault()) {
                return mainClassField;
            }

            JavaCodeFragment.VisibilityChecker visibilityChecker = (declaration, place) -> {
                if (declaration instanceof PsiClass aClass
                    && (ConfigurationUtil.MAIN_CLASS.test(aClass) && PsiMethodUtil.findMainMethod(aClass) != null
                    || place.getParent() != null && myModuleSelector.findClass(aClass.getQualifiedName()) != null)) {
                    return JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
                }
                return JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE;
            };

            CoroutineScope.launchAsync(
                myProject.coroutineContext(),
                () -> Coroutine
                    .first(ReadLock.<Void, @Nullable Document>apply(
                        ignored -> JavaReferenceEditorUtil.createDocument("", myProject, true, visibilityChecker)
                    ))
                    .then(UIAction.<@Nullable Document, Void>apply(document -> {
                        if (document != null) {
                            String text = StringUtil.notNullize(mainClassField.getValue());
                            mainClassField.setDocument(document, JavaFileType.INSTANCE);
                            mainClassField.setValue(text);
                        }
                        return null;
                    }))
            );

            if (!myProject.getApplication().isUnifiedApplication()) {
                ClassBrowser classBrowser = ClassBrowser.createApplicationClassBrowser(myProject, myModuleSelector);

                ActionGroup actions = ActionGroup.newImmutableBuilder()
                    .add(DumbAwareAction.create(
                        ExecutionLocalize.chooseMainClassDialogTitle(),
                        LocalizeValue.empty(),
                        PlatformIconGroup.nodesClass(),
                        e -> {
                            String className = classBrowser.chooseClass(mainClassField.getValue());
                            if (className != null) {
                                mainClassField.setValue(className);
                            }
                        }
                    ))
                    .build();

                ActionToolbar toolbar = ActionToolbarFactory.getInstance()
                    .createActionToolbar("ApplicationConfigurableMainClass", actions, ActionToolbar.Style.INPLACE);
                toolbar.setTargetUIComponent(mainClassField);
                toolbar.updateActionsAsync();

                mainClassField.setSuffixComponent(toolbar.getUIComponent());
            }
            return mainClassField;
        }

        @Override
        @RequiredUIAccess
        protected void addBefore(FormBuilder builder) {
            builder.addLabeled(JavaExecutionLocalize.applicationConfigurationMainClassLabel(), myMainClassField);
            super.addBefore(builder);
        }

        @Override
        @RequiredUIAccess
        protected void addAfter(FormBuilder builder) {
            builder.addLabeled(
                JavaExecutionLocalize.applicationConfigurationUseClasspathAndJdkOfModuleLabel(),
                myModuleSelector.getComponent()
            );
            builder.addLabeled(JavaExecutionLocalize.runConfigurationJreLabel(), myJrePathEditor.getComponent());
            builder.addLabeled(
                JavaExecutionLocalize.applicationConfigurationShortenCommandLineLabel(),
                myShortenCommandLineModeCombo.getComponent()
            );
            builder.addBottom(myIncludeProviderDepsBox);
        }

        @Override
        @RequiredUIAccess
        public void apply(ApplicationConfiguration configuration) {
            super.apply(configuration);

            configuration.setMainClassName(StringUtil.notNullize(myMainClassField.getValue()));

            myModuleSelector.applyTo(configuration);

            configuration.ALTERNATIVE_JRE_PATH = myJrePathEditor.getJrePathOrName();
            configuration.ALTERNATIVE_JRE_PATH_ENABLED = myJrePathEditor.isAlternativeJreSelected();
            configuration.setShortenCommandLine(myShortenCommandLineModeCombo.getSelectedItem());
            configuration.setIncludeProvidedScope(myIncludeProviderDepsBox.getValueOrError());
        }

        @Override
        @RequiredUIAccess
        public void reset(ApplicationConfiguration configuration) {
            super.reset(configuration);

            String mainClassName = configuration.getMainClassName();
            myMainClassField.setValue(mainClassName != null ? mainClassName.replace('$', '.') : "");

            myModuleSelector.reset(configuration);
            setModuleContext(myModuleSelector.getModule());
            myJrePathEditor.setByName(configuration.ALTERNATIVE_JRE_PATH_ENABLED ? configuration.ALTERNATIVE_JRE_PATH : null);
            myShortenCommandLineModeCombo.setSelectedItem(configuration.getShortenCommandLine());
            myIncludeProviderDepsBox.setValue(configuration.isProvidedScopeIncluded());
        }
    }
}
