/*
 * Copyright 2013-2026 consulo.io
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
import com.intellij.java.execution.impl.ui.BaseConfigurationModuleSelector;
import com.intellij.java.execution.impl.ui.ClassBrowser;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.ui.JavaReferenceEditorUtil;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import consulo.application.concurrent.coroutine.ReadLock;
import consulo.disposer.Disposable;
import consulo.document.Document;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.localize.ExecutionLocalize;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.language.editor.ui.EditorBox;
import consulo.language.editor.ui.EditorBoxBuilderFactory;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.UIAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.ActionToolbarFactory;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.CoroutineScope;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * The main class of an {@link ApplicationConfiguration}; completion and the class chooser look in the module of the
 * configuration.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class ApplicationMainClassFragment implements RunConfigurationFragment<ApplicationConfiguration> {
    @Override
    public String getId() {
        return "mainClass";
    }

    @Override
    public LocalizeValue getName() {
        return JavaExecutionLocalize.applicationConfigurationFragmentMainClass();
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<ApplicationConfiguration> createEditor(Project project, Disposable parent) {
        ModuleHolder moduleHolder = new ModuleHolder(project);
        EditorBox mainClassField = createMainClassField(project, moduleHolder);

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(JavaExecutionLocalize.applicationConfigurationMainClassLabel(), mainClassField);
            }

            @RequiredUIAccess
            @Override
            public void reset(ApplicationConfiguration target) {
                moduleHolder.myModule = target.getConfigurationModule().getModule();
                String mainClassName = target.getMainClassName();
                mainClassField.setValue(mainClassName != null ? mainClassName.replace('$', '.') : "");
            }

            @RequiredUIAccess
            @Override
            public void apply(ApplicationConfiguration target) {
                moduleHolder.myModule = target.getConfigurationModule().getModule();
                target.setMainClassName(StringUtil.notNullize(mainClassField.getValue()).trim());
            }
        };
    }

    @RequiredUIAccess
    private static EditorBox createMainClassField(Project project, ModuleHolder moduleHolder) {
        EditorBox mainClassField = project.getApplication().getInstance(EditorBoxBuilderFactory.class).create(project).build();
        if (project.isDefault()) {
            return mainClassField;
        }

        JavaCodeFragment.VisibilityChecker visibilityChecker = (declaration, place) -> {
            if (declaration instanceof PsiClass aClass
                && (ConfigurationUtil.MAIN_CLASS.test(aClass) && PsiMethodUtil.findMainMethod(aClass) != null
                || place.getParent() != null && moduleHolder.findClass(aClass.getQualifiedName()) != null)) {
                return JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
            }
            return JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE;
        };

        CoroutineScope.launchAsync(
            project.coroutineContext(),
            () -> Coroutine
                .first(ReadLock.<Void, @Nullable Document>apply(
                    ignored -> JavaReferenceEditorUtil.createDocument("", project, true, visibilityChecker)
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

        if (!project.getApplication().isUnifiedApplication()) {
            ClassBrowser classBrowser = ClassBrowser.createApplicationClassBrowser(project, moduleHolder);

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

    /**
     * The module of the configuration as last reset or applied, for completion and the class chooser.
     */
    private static class ModuleHolder extends BaseConfigurationModuleSelector {
        private volatile @Nullable Module myModule;

        private ModuleHolder(Project project) {
            super(project);
        }

        @Override
        public void applyTo(ModuleBasedConfiguration configurationModule) {
        }

        @Override
        public void reset(ModuleBasedConfiguration configuration) {
            myModule = configuration.getConfigurationModule().getModule();
        }

        @Override
        public @Nullable Module getModule() {
            return myModule;
        }

        @Override
        protected void setModules(Collection<Module> modules) {
        }
    }
}
