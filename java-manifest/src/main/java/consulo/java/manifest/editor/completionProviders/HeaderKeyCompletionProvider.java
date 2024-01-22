package consulo.java.manifest.editor.completionProviders;

import consulo.java.manifest.internal.header.ManifestHeaderParserRegistratorImpl;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.ui.awt.TextFieldWithAutoCompletionListProvider;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import org.osmorc.manifest.lang.ManifestFileType;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author VISTALL
 * @since 13:55/05.05.13
 */
public class HeaderKeyCompletionProvider extends TextFieldWithAutoCompletionListProvider<String> {
  public HeaderKeyCompletionProvider() {
    super(null);
  }

  @Nonnull
  @Override
  public Collection<String> getItems(String prefix, boolean cached, CompletionParameters parameters) {
    ManifestHeaderParserRegistratorImpl registrator = ManifestHeaderParserRegistratorImpl.get();

    List<String> list = new ArrayList<>(registrator.getParsers().size());
    list.addAll(registrator.getParsers().keySet());
    return list;
  }

  @jakarta.annotation.Nullable
  @Override
  protected Image getIcon(@jakarta.annotation.Nonnull String item) {
    return ManifestFileType.INSTANCE.getIcon();
  }

  @jakarta.annotation.Nonnull
  @Override
  protected String getLookupString(@jakarta.annotation.Nonnull String item) {
    return item;
  }

  @Nullable
  @Override
  protected String getTailText(@jakarta.annotation.Nonnull String item) {
    return null;
  }

  @Nullable
  @Override
  protected String getTypeText(@jakarta.annotation.Nonnull String item) {
    return null;
  }

  @Override
  public int compare(String item1, String item2) {
    return item1.compareTo(item2);
  }
}
