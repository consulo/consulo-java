/*
 * Copyright 2013-2017 consulo.io
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

package consulo.java.impl.application.options;

import com.intellij.java.language.codeInsight.folding.JavaCodeFoldingSettings;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.java.localize.JavaLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 21-Jun-17
 */
@ExtensionImpl
public class JavaCodeFoldingConfigurable extends SimpleConfigurableByProperties implements ApplicationConfigurable {
    private Provider<JavaCodeFoldingSettings> myJavaCodeFoldingSettingsProvider;

    @Inject
    public JavaCodeFoldingConfigurable(Provider<JavaCodeFoldingSettings> javaCodeFoldingSettingsProvider) {
        myJavaCodeFoldingSettingsProvider = javaCodeFoldingSettingsProvider;
    }

    @Nonnull
    @Override
    public String getId() {
        return "editor.preferences.folding.java";
    }

    @Nullable
    @Override
    public String getParentId() {
        return "editor.preferences.folding";
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return "Java";
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    protected Component createLayout(@Nonnull PropertyBuilder propertyBuilder, @Nonnull Disposable uiDisposable) {
        VerticalLayout layout = VerticalLayout.create();

        JavaCodeFoldingSettings settings = myJavaCodeFoldingSettingsProvider.get();

        checkBox(JavaLocalize.checkboxCollapseOneLineMethods(),
            layout,
            propertyBuilder,
            settings::isCollapseOneLineMethods,
            settings::setCollapseOneLineMethods);

        checkBox(JavaLocalize.checkboxCollapseSimplePropertyAccessors(),
            layout,
            propertyBuilder,
            settings::isCollapseAccessors,
            settings::setCollapseAccessors);

        checkBox(JavaLocalize.checkboxCollapseInnerClasses(),
            layout,
            propertyBuilder,
            settings::isCollapseInnerClasses,
            settings::setCollapseInnerClasses);

        checkBox(JavaLocalize.checkboxCollapseAnonymousClasses(),
            layout,
            propertyBuilder,
            settings::isCollapseAnonymousClasses,
            settings::setCollapseAnonymousClasses);

        checkBox(JavaLocalize.checkboxCollapseAnnotations(),
            layout,
            propertyBuilder,
            settings::isCollapseAnnotations,
            settings::setCollapseAnnotations);

        checkBox(JavaLocalize.checkboxCollapseClosures(),
            layout,
            propertyBuilder,
            settings::isCollapseLambdas,
            settings::setCollapseLambdas);

        checkBox(JavaLocalize.checkboxCollapseGenericConstructorParameters(),
            layout,
            propertyBuilder,
            settings::isCollapseConstructorGenericParameters,
            settings::setCollapseConstructorGenericParameters);

        checkBox(JavaLocalize.checkboxCollapseI18nMessages(),
            layout,
            propertyBuilder,
            settings::isCollapseI18nMessages,
            settings::setCollapseI18nMessages);

        checkBox(JavaLocalize.checkboxCollapseSuppressWarnings(),
            layout,
            propertyBuilder,
            settings::isCollapseSuppressWarnings,
            settings::setCollapseSuppressWarnings);

        checkBox(JavaLocalize.checkboxCollapseEndOfLineComments(),
            layout,
            propertyBuilder,
            settings::isCollapseEndOfLineComments,
            settings::setCollapseEndOfLineComments);
        return layout;
    }

    @RequiredUIAccess
    private void checkBox(LocalizeValue text,
                          VerticalLayout layout,
                          @Nonnull PropertyBuilder builder,
                          @Nonnull Supplier<Boolean> getter,
                          @Nonnull Consumer<Boolean> setter) {
        CheckBox checkBox = CheckBox.create(text);
        builder.add(checkBox, getter, setter);
        layout.add(checkBox);
    }
}
