package consulo.java.ide.newProjectOrModule;

import com.intellij.openapi.projectRoots.Sdk;
import consulo.ide.wizard.newModule.NewModuleWizardContextBase;

/**
 * @author VISTALL
 * @since 2019-09-01
 */
public class JavaNewModuleWizardContext extends NewModuleWizardContextBase
{
	private Sdk mySdk;

	public JavaNewModuleWizardContext(boolean isNewProject)
	{
		super(isNewProject);
	}

	public void setSdk(Sdk sdk)
	{
		mySdk = sdk;
	}

	public Sdk getSdk()
	{
		return mySdk;
	}
}
