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

package consulo.java.application.options;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import consulo.java.JavaBundle;
import consulo.options.SimpleConfigurableByProperties;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 21-Jun-17
 */
public class JavaCodeFoldingConfigurable extends SimpleConfigurableByProperties implements Configurable
{
	@RequiredUIAccess
	@Nonnull
	@Override
	protected Component createLayout(PropertyBuilder propertyBuilder)
	{
		VerticalLayout layout = VerticalLayout.create();

		JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();

		checkBox(JavaBundle.message("checkbox.collapse.one.line.methods"), layout, propertyBuilder, settings::isCollapseOneLineMethods, settings::setCollapseOneLineMethods);

		checkBox(ApplicationBundle.message("checkbox.collapse.simple.property.accessors"), layout, propertyBuilder, settings::isCollapseAccessors, settings::setCollapseAccessors);

		checkBox(ApplicationBundle.message("checkbox.collapse.inner.classes"), layout, propertyBuilder, settings::isCollapseInnerClasses, settings::setCollapseInnerClasses);

		checkBox(ApplicationBundle.message("checkbox.collapse.anonymous.classes"), layout, propertyBuilder, settings::isCollapseAnonymousClasses, settings::setCollapseAnonymousClasses);

		checkBox(ApplicationBundle.message("checkbox.collapse.annotations"), layout, propertyBuilder, settings::isCollapseAnnotations, settings::setCollapseAnnotations);

		checkBox(ApplicationBundle.message("checkbox.collapse.closures"), layout, propertyBuilder, settings::isCollapseLambdas, settings::setCollapseLambdas);

		checkBox(ApplicationBundle.message("checkbox.collapse.generic.constructor.parameters"), layout, propertyBuilder, settings::isCollapseConstructorGenericParameters,
				settings::setCollapseConstructorGenericParameters);

		checkBox(ApplicationBundle.message("checkbox.collapse.i18n.messages"), layout, propertyBuilder, settings::isCollapseI18nMessages, settings::setCollapseI18nMessages);

		checkBox(ApplicationBundle.message("checkbox.collapse.suppress.warnings"), layout, propertyBuilder, settings::isCollapseSuppressWarnings, settings::setCollapseSuppressWarnings);

		checkBox(ApplicationBundle.message("checkbox.collapse.end.of.line.comments"), layout, propertyBuilder, settings::isCollapseEndOfLineComments, settings::setCollapseEndOfLineComments);
		return layout;
	}

	@RequiredUIAccess
	private void checkBox(String text, VerticalLayout layout, @Nonnull PropertyBuilder builder, @Nonnull Supplier<Boolean> getter, @Nonnull Consumer<Boolean> setter)
	{
		CheckBox checkBox = CheckBox.create(text);
		builder.add(checkBox, getter, setter);
		layout.add(checkBox);
	}
}
