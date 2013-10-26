package org.osmorc.manifest.editor.completionProviders;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osmorc.manifest.lang.headerparser.HeaderParser;
import org.osmorc.manifest.lang.psi.Clause;
import org.osmorc.manifest.lang.psi.Header;
import org.osmorc.manifest.lang.psi.HeaderValuePart;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
* @author VISTALL
* @since 8:57/04.05.13
*/
public class HeaderValueCompletionProvider extends TextFieldWithAutoCompletionListProvider<Object> {

  private final Header myHeaderByName;
  private final HeaderParser myHeaderParser;

  public HeaderValueCompletionProvider(Header headerByName, HeaderParser headerParser) {
    super(null);
    myHeaderByName = headerByName;
    myHeaderParser = headerParser;
  }

  @NotNull
  @Override
  public Collection<Object> getItems(String prefix, boolean cached, CompletionParameters parameters) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<Object>>() {
      @Override
      public Collection<Object> compute() {
        Clause[] clauses = myHeaderByName.getClauses();
        if (clauses.length == 0) {
          return Collections.emptyList();
        }
        HeaderValuePart value = clauses[0].getValue();
        if (value == null) {
          return Collections.emptyList();
        }

        List<Object> objects = new ArrayList<Object>();
        PsiReference[] references = myHeaderParser.getReferences(value);
        for (PsiReference reference : references) {
          for (Object o : reference.getVariants()) {
            if(myHeaderParser.isAcceptable(o)) {
              objects.add(o);
            }
          }
        }
        return objects;
      }
    });
  }

  @Nullable
  @Override
  protected Icon getIcon(@NotNull Object item) {
    if (item instanceof NavigationItem) {
      ItemPresentation itemPresentation = ItemPresentationProviders.getItemPresentation((NavigationItem)item);
      if (itemPresentation != null) {
        return itemPresentation.getIcon(false);
      }
    }
    return null;
  }

  @NotNull
  @Override
  protected String getLookupString(@NotNull Object item) {
    if (item instanceof PsiElement) {
      for (QualifiedNameProvider provider : Extensions.getExtensions(QualifiedNameProvider.EP_NAME)) {
        String result = provider.getQualifiedName((PsiElement)item);
        if (result != null) {
          return result;
        }
      }
    }
    return item.toString();
  }

  @Nullable
  @Override
  protected String getTailText(@NotNull Object item) {
    return null;
  }

  @Nullable
  @Override
  protected String getTypeText(@NotNull Object item) {
    return null;
  }

  @Override
  public int compare(Object item1, Object item2) {
    return 0;
  }
}
