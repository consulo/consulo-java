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

package consulo.java.impl.ide.newProjectOrModule;

import com.intellij.java.language.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.SdkTable;
import consulo.bundle.ui.BundleBox;
import consulo.bundle.ui.BundleBoxBuilder;
import consulo.disposer.Disposable;
import consulo.ide.newProject.ui.UnifiedProjectOrModuleNameStep;
import consulo.localize.LocalizeValue;
import consulo.ui.ComboBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.util.FormBuilder;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 05.06.14
 */
public class JavaSdkSelectStep extends UnifiedProjectOrModuleNameStep<JavaNewModuleWizardContext>
{
	private BundleBox myBundleBox;

	public JavaSdkSelectStep(@Nonnull JavaNewModuleWizardContext context)
	{
		super(context);
	}

	@RequiredUIAccess
	@Override
	protected void extend(@Nonnull FormBuilder builder, @Nonnull Disposable uiDisposable)
	{
		super.extend(builder, uiDisposable);

		BundleBoxBuilder boxBuilder = BundleBoxBuilder.create(uiDisposable);
		boxBuilder.withSdkTypeFilter(sdkTypeId -> sdkTypeId instanceof JavaSdk);

		builder.addLabeled(LocalizeValue.localizeTODO("JDK:"), (myBundleBox = boxBuilder.build()).getComponent());

		ComboBox<BundleBox.BundleBoxItem> component = myBundleBox.getComponent();
		if(component.getListModel().getSize() > 0)
		{
			component.setValueByIndex(0);
		}
	}

	@Override
	public void onStepLeave(@Nonnull JavaNewModuleWizardContext context)
	{
		super.onStepLeave(context);

		String selectedBundleName = myBundleBox.getSelectedBundleName();
		if(selectedBundleName != null)
		{
			context.setSdk(SdkTable.getInstance().findSdk(selectedBundleName));
		}
	}
}