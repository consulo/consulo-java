package consulo.java.execution.impl.util;

import consulo.process.ExecutionException;
import com.intellij.java.language.projectRoots.JavaSdk;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import com.intellij.java.language.LanguageLevel;
import consulo.platform.Platform;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author VISTALL
 * @since 29/12/2020
 */
public class JreSearchUtil {
  public static final String USE_JAVA_HOME = "#JAVA_HOME";

  @Nullable
  private Sdk getSdkForRun(@Nonnull SdkTable sdkTable, @jakarta.annotation.Nullable String jreHome, @Nonnull LanguageLevel languageLevel) throws ExecutionException {
    if (USE_JAVA_HOME.equals(jreHome)) {
      final String javaHome = Platform.current().os().getEnvironmentVariable("JAVA_HOME");
      if (StringUtil.isEmptyOrSpaces(javaHome)) {
        throw new ExecutionException("JAVA_HOME is not defined");
      }
      final Sdk jdk = JavaSdk.getInstance().createJdk("", javaHome);
      if (jdk == null) {
        throw new ExecutionException(javaHome + " is wrong JRE home");
      }
      return jdk;
    }

    if (jreHome != null) {
      return sdkTable.findSdk(jreHome);
    }

    return findSdkOfLevel(sdkTable, languageLevel, null);
  }

  @jakarta.annotation.Nullable
  public static Sdk findSdkOfLevel(@Nonnull SdkTable sdkTable, @Nonnull LanguageLevel languageLevel, @jakarta.annotation.Nullable String runtimeJdkName) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    if (runtimeJdkName != null) {
      Sdk sdk = sdkTable.findSdk(runtimeJdkName);
      if (sdk != null) {
        JavaSdkVersion version = javaSdk.getVersion(sdk);
        if (version != null && version.getMaxLanguageLevel().isAtLeast(languageLevel)) {
          return sdk;
        }
      }
    }

    List<Sdk> sdks = sdkTable.getSdksOfType(javaSdk);
    ContainerUtil.weightSort(sdks, sdk ->
    {
      JavaSdkVersion version = javaSdk.getVersion(sdk);
      int ordinal = version == null ? 0 : version.ordinal();
      return sdk.isPredefined() ? ordinal * 100 : ordinal;
    });

    for (Sdk sdk : sdks) {
      JavaSdkVersion version = javaSdk.getVersion(sdk);
      if (version == null) {
        continue;
      }

      if (version.getMaxLanguageLevel().isAtLeast(languageLevel)) {
        return sdk;
      }
    }

    return null;
  }
}
