package consulo.java.manifest.editor.completionProviders;

import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.ui.awt.TextFieldWithAutoCompletionListProvider;
import consulo.java.manifest.lang.headerparser.HeaderParserEP;
import consulo.ui.image.Image;
import org.osmorc.manifest.lang.ManifestFileType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    List<HeaderParserEP> extensions = HeaderParserEP.EP_NAME.getExtensionList();
    List<String> list = new ArrayList<>(extensions.size() - 1);
    for (HeaderParserEP ep : extensions) {
      if (ep.key.isEmpty()) {
        continue;
      }
      list.add(ep.key);
    }
    return list;
  }

  @Nullable
  @Override
  protected Image getIcon(@Nonnull String item) {
    return ManifestFileType.INSTANCE.getIcon();
  }

  @Nonnull
  @Override
  protected String getLookupString(@Nonnull String item) {
    return item;
  }

  @Nullable
  @Override
  protected String getTailText(@Nonnull String item) {
    return null;
  }

  @Nullable
  @Override
  protected String getTypeText(@Nonnull String item) {
    return null;
  }

  @Override
  public int compare(String item1, String item2) {
    return item1.compareTo(item2);
  }
}
