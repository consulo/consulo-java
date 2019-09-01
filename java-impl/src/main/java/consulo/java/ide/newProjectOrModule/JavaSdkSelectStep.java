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

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Conditions;
import consulo.ide.newProject.ui.ProjectOrModuleNameStep;
import consulo.roots.ui.configuration.SdkComboBox;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;

/**
 * @author VISTALL
 * @since 05.06.14
 */
public class JavaSdkSelectStep extends ProjectOrModuleNameStep<JavaNewModuleWizardContext>
{
	private SdkComboBox myComboBox;

	public JavaSdkSelectStep(JavaNewModuleWizardContext context)
	{
		super(context);

		myComboBox = new SdkComboBox(SdkTable.getInstance(), Conditions.instanceOf(JavaSdk.class), false);

		myAdditionalContentPanel.add(LabeledComponent.create(myComboBox, "JDK"), BorderLayout.NORTH);
	}

	@Override
	public void onStepLeave(@Nonnull JavaNewModuleWizardContext context)
	{
		super.onStepLeave(context);

		context.setSdk(myComboBox.getSelectedSdk());
	}

	@Nullable
	public Sdk getSdk()
	{
		return myComboBox.getSelectedSdk();
	}
}
