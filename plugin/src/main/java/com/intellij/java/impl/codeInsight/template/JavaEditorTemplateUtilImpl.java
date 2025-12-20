package com.intellij.java.impl.codeInsight.template;

import com.intellij.java.impl.codeInsight.lookup.LookupItemUtil;
import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.impl.codeInsight.template.impl.JavaTemplateLookupSelectionHandler;
import com.intellij.java.language.psi.PsiType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.template.TemplateLookupSelectionHandler;
import consulo.language.psi.PsiElement;

import java.util.Set;

/**
 * @author VISTALL
 * @since 20/12/2022
 */
public class JavaEditorTemplateUtilImpl {
  public static LookupElement addElementLookupItem(Set<LookupElement> items, PsiElement element) {
    LookupElement item = LookupItemUtil.objectToLookupItem(element);
    items.add(item);
    item.putUserData(TemplateLookupSelectionHandler.KEY_IN_LOOKUP_ITEM, new JavaTemplateLookupSelectionHandler());
    return item;
  }

  public static LookupElement addTypeLookupItem(Set<LookupElement> items, PsiType type) {
    LookupElement item = PsiTypeLookupItem.createLookupItem(type, null);
    items.add(item);
    item.putUserData(TemplateLookupSelectionHandler.KEY_IN_LOOKUP_ITEM, new JavaTemplateLookupSelectionHandler());
    return item;
  }
}
