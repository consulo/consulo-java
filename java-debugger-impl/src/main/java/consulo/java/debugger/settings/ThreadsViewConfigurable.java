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

package consulo.java.debugger.settings;

import javax.annotation.Nonnull;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.openapi.options.Configurable;
import consulo.options.SimpleConfigurableByProperties;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.LabeledLayout;
import consulo.ui.RequiredUIAccess;
import consulo.ui.VerticalLayout;

/**
 * @author VISTALL
 * @since 19-Nov-17
 */
public class ThreadsViewConfigurable extends SimpleConfigurableByProperties implements Configurable
{
	@RequiredUIAccess
	@Nonnull
	@Override
	protected Component createLayout(PropertyBuilder propertyBuilder)
	{
		ThreadsViewSettings settings = ThreadsViewSettings.getInstance();

		VerticalLayout rootLayout = VerticalLayout.create();

		VerticalLayout viewLayout = VerticalLayout.create();
		rootLayout.add(LabeledLayout.create("View", viewLayout));

		CheckBox showThreadGroups = CheckBox.create(DebuggerBundle.message("label.threads.view.configurable.show.thread.groups"));
		propertyBuilder.add(showThreadGroups, () -> settings.SHOW_THREAD_GROUPS, value -> settings.SHOW_THREAD_GROUPS = value);
		viewLayout.add(showThreadGroups);

		CheckBox showSynthetic = CheckBox.create(DebuggerBundle.message("label.threads.view.configurable.show.stack.frames.for.synthetic.methods"));
		propertyBuilder.add(showSynthetic, () -> settings.SHOW_SYNTHETIC_FRAMES, value -> settings.SHOW_SYNTHETIC_FRAMES = value);
		viewLayout.add(showSynthetic);

		CheckBox moveCurrentThreadToTop = CheckBox.create(DebuggerBundle.message("label.threads.view.configurable.current.thread.on.top"));
		propertyBuilder.add(moveCurrentThreadToTop, () -> settings.SHOW_CURRENT_THREAD, value -> settings.SHOW_CURRENT_THREAD = value);
		viewLayout.add(moveCurrentThreadToTop);

		VerticalLayout presentationView = VerticalLayout.create();
		rootLayout.add(LabeledLayout.create("Presentation", presentationView));

		CheckBox showLineNumbers = CheckBox.create(DebuggerBundle.message("label.threads.view.configurable.show.line.number"));
		propertyBuilder.add(showLineNumbers, () -> settings.SHOW_LINE_NUMBER, value -> settings.SHOW_LINE_NUMBER = value);
		presentationView.add(showLineNumbers);

		CheckBox showClassName = CheckBox.create(DebuggerBundle.message("label.threads.view.configurable.show.class.name"));
		propertyBuilder.add(showClassName, () -> settings.SHOW_CLASS_NAME, value -> settings.SHOW_CLASS_NAME = value);
		presentationView.add(showClassName);

		CheckBox showPackageName = CheckBox.create(DebuggerBundle.message("label.threads.view.configurable.show.package"));
		propertyBuilder.add(showPackageName, () -> settings.SHOW_PACKAGE_NAME, value -> settings.SHOW_PACKAGE_NAME = value);
		presentationView.add(showPackageName);

		CheckBox showSourceFileName = CheckBox.create(DebuggerBundle.message("label.threads.view.configurable.show.source.file.name"));
		propertyBuilder.add(showSourceFileName, () -> settings.SHOW_SOURCE_NAME, value -> settings.SHOW_SOURCE_NAME = value);
		presentationView.add(showSourceFileName);

		CheckBox showMethodArgumentTypes = CheckBox.create(DebuggerBundle.message("label.threads.view.configurable.show.prams.types"));
		propertyBuilder.add(showMethodArgumentTypes, () -> settings.SHOW_ARGUMENTS_TYPES, value -> settings.SHOW_ARGUMENTS_TYPES = value);
		presentationView.add(showMethodArgumentTypes);

		return rootLayout;
	}

	@Override
	public String getDisplayName()
	{
		return DebuggerBundle.message("threads.view.configurable.display.name");
	}
}
