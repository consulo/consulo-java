/*
 * Copyright 2013-2014 must-be.org
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

package consulo.java.ide.newProjectOrModule;

import java.awt.BorderLayout;

import javax.swing.JPanel;

import javax.annotation.Nullable;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Conditions;
import consulo.roots.ui.configuration.SdkComboBox;

/**
 * @author VISTALL
 * @since 05.06.14
 */
public class JavaNewModuleBuilderPanel extends JPanel
{
	private SdkComboBox myComboBox;

	public JavaNewModuleBuilderPanel()
	{
		super(new VerticalFlowLayout());

		ProjectSdksModel sdksModel = new ProjectSdksModel();
		sdksModel.reset();

		myComboBox = new SdkComboBox(sdksModel, Conditions.instanceOf(JavaSdk.class), false);

		add(LabeledComponent.create(myComboBox, "JDK").setLabelLocation(BorderLayout.WEST));
	}

	@Nullable
	public Sdk getSdk()
	{
		return myComboBox.getSelectedSdk();
	}
}
