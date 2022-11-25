package consulo.java.impl.ide.newProjectOrModule;

import consulo.content.bundle.Sdk;
import consulo.ide.newModule.NewModuleWizardContextBase;

/**
 * @author VISTALL
 * @since 2019-09-01
 */
public class JavaNewModuleWizardContext extends NewModuleWizardContextBase {
  private Sdk mySdk;

  public JavaNewModuleWizardContext(boolean isNewProject) {
    super(isNewProject);
  }

  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }

  public Sdk getSdk() {
    return mySdk;
  }
}
