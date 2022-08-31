package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.icons.AllIcons;
import com.intellij.util.Consumer;
import com.intellij.util.ui.EmptyIcon;
import consulo.ui.image.Image;

import javax.swing.*;

/**
 * @author peter
 */
public class ImportStaticLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(final LookupElement element, Lookup lookup, Consumer<LookupElementAction> consumer) {
    final StaticallyImportable item = element.as(StaticallyImportable.CLASS_CONDITION_KEY);
    if (item == null || !item.canBeImported()) {
      return;
    }

    final Image checkIcon = AllIcons.Actions.Checked;
    final Image icon = item.willBeImported() ? checkIcon : Image.empty(checkIcon.getWidth(), checkIcon.getHeight());
    consumer.consume(new LookupElementAction(icon, "Import statically") {
      @Override
      public Result performLookupAction() {
        item.setShouldBeImported(!item.willBeImported());
        return new Result.ChooseItem(element);
      }
    });
  }
}
