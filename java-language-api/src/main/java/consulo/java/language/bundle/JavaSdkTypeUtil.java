package consulo.java.language.bundle;

import com.intellij.java.language.projectRoots.JavaSdkType;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author VISTALL
 * @since 01.06.2024
 */
public class JavaSdkTypeUtil {
  public static List<Sdk> getAllJavaSdks() {
    return ContainerUtil.filter(SdkTable.getInstance().getAllSdks(), sdk -> sdk.getSdkType() instanceof JavaSdkType);
  }

  @Nullable
  public static JavaSdkVersion getVersion(Sdk sdk) {
    String version = sdk.getVersionString();
    return version == null ? null : JavaSdkVersion.fromVersionString(version);
  }

  @Nullable
  public static JavaSdkVersion getVersion(String versionString) {
    return JavaSdkVersion.fromVersionString(versionString);
  }

  public static boolean isOfVersionOrHigher(Sdk sdk, JavaSdkVersion version) {
    JavaSdkVersion sdkVersion = getVersion(sdk);
    return sdkVersion != null && sdkVersion.isAtLeast(version);
  }
}
