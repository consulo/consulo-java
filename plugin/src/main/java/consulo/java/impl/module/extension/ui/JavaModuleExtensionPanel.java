/*
 * Copyright 2013-2015 must-be.org
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
package consulo.java.impl.module.extension.ui;

import com.intellij.java.compiler.impl.javaCompiler.TargetOptionsComponent;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.disposer.Disposable;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.language.localize.JavaCoreLocalize;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.java.language.module.extension.JavaMutableModuleExtension;
import consulo.java.language.module.extension.SpecialDirLocation;
import consulo.java.localize.JavaLocalize;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.ModuleExtensionWithSdk;
import consulo.module.extension.MutableModuleInheritableNamedPointer;
import consulo.module.ui.extension.ModuleExtensionBundleBoxBuilder;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.InputBoxBuilder;
import consulo.ui.Label;
import consulo.ui.ListBox;
import consulo.ui.RadioGroup;
import consulo.ui.TextAttribute;
import consulo.ui.TextBoxWithHistory;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionToolbarPosition;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.toolbar.AddAction;
import consulo.ui.ex.toolbar.ToolbarDecoratorBuilderFactory;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2015-03-15
 */
public final class JavaModuleExtensionPanel {
    private JavaModuleExtensionPanel() {
    }

    @RequiredUIAccess
    public static Component create(JavaMutableModuleExtension<?> extension, Disposable uiDisposable, Runnable classpathStateUpdater) {
        ComboBox<Object> languageLevelBox = createLanguageLevelBox(extension);

        ModuleExtensionBundleBoxBuilder<?> sdkBoxBuilder =
            ModuleExtensionBundleBoxBuilder.createAndDefine(extension, uiDisposable, classpathStateUpdater);
        sdkBoxBuilder.postConsumer((oldValue, newValue) -> {
            Object selectedItem = languageLevelBox.getValue();
            if (selectedItem instanceof LanguageLevel && newValue != null && oldValue != null) {
                JavaSdkVersion oldSdkVersion = JavaSdkTypeUtil.getVersion(oldValue);

                if (oldSdkVersion != null && oldSdkVersion.getMaxLanguageLevel() == selectedItem) {
                    JavaSdkVersion newSdkVersion = JavaSdkTypeUtil.getVersion(newValue);
                    if (newSdkVersion != null) {
                        languageLevelBox.setValue(newSdkVersion.getMaxLanguageLevel());
                    }
                }
            }
        });

        FormBuilder languageForm = FormBuilder.create();
        languageForm.addLabeled(JavaLocalize.moduleExtensionLanguageLevelLabel(), languageLevelBox);

        RadioGroup<SpecialDirLocation> specialDirGroup = RadioGroup.create();
        VerticalLayout specialDirLayout = VerticalLayout.create();
        specialDirLayout.add(specialDirGroup.newButton(JavaLocalize.moduleExtensionSpecialDirModule(), SpecialDirLocation.MODULE_DIR));
        specialDirLayout.add(specialDirGroup.newButton(JavaLocalize.moduleExtensionSpecialDirSource(), SpecialDirLocation.SOURCE_DIR));
        SpecialDirLocation specialDirLocation = extension.getSpecialDirLocation();
        specialDirGroup.setValue(
            specialDirLocation == SpecialDirLocation.MODULE_DIR ? SpecialDirLocation.MODULE_DIR : SpecialDirLocation.SOURCE_DIR,
            false
        );
        specialDirGroup.addValueListener(location -> {
            if (location != null) {
                extension.setSpecialDirLocation(location);
            }
        });

        List<String> bytecodeVersions = new ArrayList<>(LanguageLevel.getAllCompilerOptions());
        Collections.reverse(bytecodeVersions);

        TextBoxWithHistory bytecodeVersionBox = TextBoxWithHistory.create(StringUtil.notNullize(extension.getBytecodeVersion()));
        bytecodeVersionBox.setHistory(bytecodeVersions);
        bytecodeVersionBox.setPlaceholder(LocalizeValue.of(TargetOptionsComponent.COMPILER_DEFAULT));
        bytecodeVersionBox.addValueListener(event -> extension.setBytecodeVersion(StringUtil.nullize(event.getValue(), true)));

        FormBuilder compilerForm = FormBuilder.create();
        compilerForm.addLabeled(JavaLocalize.moduleExtensionBytecodeVersionLabel(), bytecodeVersionBox);

        VerticalLayout compilerTopLayout = VerticalLayout.create();
        compilerTopLayout.add(compilerForm.build());
        compilerTopLayout.add(Label.create(JavaLocalize.moduleExtensionCompilerArgumentsLabel()));

        DockLayout compilerLayout = DockLayout.create();
        compilerLayout.top(compilerTopLayout);
        compilerLayout.center(createCompilerArgumentList(extension));

        VerticalLayout topLayout = VerticalLayout.create();
        topLayout.add(sdkBoxBuilder.build());
        topLayout.add(languageForm.build());
        topLayout.add(LabeledLayout.create(JavaCoreLocalize.pathsToSpecialRoots(), specialDirLayout));

        DockLayout layout = DockLayout.create();
        layout.top(topLayout);
        layout.center(LabeledLayout.create(JavaLocalize.moduleExtensionCompilerOptionsGroup(), compilerLayout));
        return layout;
    }

    @RequiredUIAccess
    private static Component createCompilerArgumentList(JavaMutableModuleExtension<?> extension) {
        MutableFlatDataModel<String> model = FlatDataModel.of(extension.getCompilerArguments());
        model.addListener(event -> {
            List<String> arguments = new ArrayList<>();
            for (String argument : model) {
                arguments.add(argument);
            }
            extension.setCompilerArguments(arguments);
        });

        ListBox<String> listBox = ListBox.create(model);

        return ToolbarDecoratorBuilderFactory.getInstance()
            .create(listBox)
            .withToolbarPosition(ActionToolbarPosition.RIGHT)
            .addOrReplaceAction(new AddAction<>() {
                @Override
                @RequiredUIAccess
                protected void doAdd(AnActionEvent e) {
                    UIAccess uiAccess = UIAccess.current();
                    InputBoxBuilder.text()
                        .title(JavaLocalize.moduleExtensionCompilerArgumentTitle())
                        .text(JavaLocalize.moduleExtensionCompilerArgumentMessage())
                        .showAsync(listBox)
                        .whenCompleteAsync((value, error) -> {
                            if (error == null && value != null) {
                                model.add(value);
                            }
                        }, uiAccess);
                }
            })
            .build();
    }

    @RequiredUIAccess
    private static ComboBox<Object> createLanguageLevelBox(JavaMutableModuleExtension<?> extension) {
        List<Object> items = new ArrayList<>();
        items.add(ObjectUtil.NULL);
        items.addAll(List.of(LanguageLevel.values()));

        Map<Module, Integer> inheritedLevels = new HashMap<>();
        for (Module module : ModuleManager.getInstance(extension.getModule().getProject()).getModules()) {
            if (module == extension.getModule()) {
                continue;
            }

            ModuleExtension<?> moduleExtension = ModuleUtilCore.getExtension(module, extension.getId());
            if (moduleExtension instanceof ModuleExtensionWithSdk<?> sdkExtension) {
                if (sdkExtension.getInheritableSdk().getModule() == extension.getModule()) {
                    continue;
                }
                items.add(module);

                JavaModuleExtension<?> javaExtension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
                if (javaExtension != null) {
                    inheritedLevels.put(module, javaExtension.getLanguageLevel().getMajor());
                }
            }
        }

        MutableModuleInheritableNamedPointer<LanguageLevel> inheritableLanguageLevel = extension.getInheritableLanguageLevel();

        Object selected = ObjectUtil.notNull(inheritableLanguageLevel.get(), ObjectUtil.NULL);
        String moduleName = inheritableLanguageLevel.getModuleName();
        if (moduleName != null) {
            Module module = inheritableLanguageLevel.getModule();
            if (module != null) {
                selected = module;
            }
            else {
                items.add(moduleName);
                selected = moduleName;
            }
        }

        ComboBox<Object> languageLevelBox = ComboBox.create(items);
        languageLevelBox.setRenderer((presentation, item) -> {
            Object value = item.getValue();
            if (value == ObjectUtil.NULL) {
                presentation.append(TargetOptionsComponent.COMPILER_DEFAULT, TextAttribute.GRAYED);
            }
            else if (value instanceof LanguageLevel languageLevel) {
                presentation.append(languageLevel.getDescription(), TextAttribute.GRAYED);
            }
            else if (value instanceof Module module) {
                presentation.withIcon(PlatformIconGroup.nodesModule());
                presentation.append(module.getName(), TextAttribute.REGULAR_BOLD);

                Integer major = inheritedLevels.get(module);
                if (major != null) {
                    presentation.append("(" + major + ")", TextAttribute.GRAYED);
                }
            }
            else if (value instanceof String name) {
                presentation.withIcon(PlatformIconGroup.nodesModule());
                presentation.append(name, TextAttribute.ERROR_BOLD);
            }
        });
        languageLevelBox.setValue(selected);
        languageLevelBox.addValueListener(event -> {
            Object value = event.getValue();
            if (value instanceof Module module) {
                inheritableLanguageLevel.set(module.getName(), null);
            }
            else if (value instanceof LanguageLevel languageLevel) {
                inheritableLanguageLevel.set(null, languageLevel.getName());
            }
            else if (value == ObjectUtil.NULL) {
                inheritableLanguageLevel.set((String) null, null);
            }
            else if (value != null) {
                inheritableLanguageLevel.set(value.toString(), null);
            }
        });
        return languageLevelBox;
    }
}
