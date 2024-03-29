package consulo.java.impl.codeInsight;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 13.10.2015
 */
@Singleton
@State(
  name = "JavaCodeInsightSettings",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/editor.codeinsight.xml")
  })
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class JavaCodeInsightSettings implements PersistentStateComponent<JavaCodeInsightSettings> {
  @Nonnull
  public static JavaCodeInsightSettings getInstance() {
    return ServiceManager.getService(JavaCodeInsightSettings.class);
  }

  public boolean USE_INSTANCEOF_ON_EQUALS_PARAMETER = false;
  public boolean USE_ACCESSORS_IN_EQUALS_HASHCODE = false;
  public boolean SHOW_SOURCE_INFERRED_ANNOTATIONS = true;

  @Nullable
  @Override
  public JavaCodeInsightSettings getState() {
    return this;
  }

  @Override
  public void loadState(JavaCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
