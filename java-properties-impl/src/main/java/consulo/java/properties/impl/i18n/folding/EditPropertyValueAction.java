package consulo.java.properties.impl.i18n.folding;

import consulo.codeEditor.FoldRegion;
import consulo.document.Document;
import consulo.language.editor.folding.EditorFoldingInfo;
import consulo.language.psi.PsiElement;
import consulo.util.dataholder.Key;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 08/12/2022
 * <p>
 * Just stub since we don't merge all this features for now
 */
public class EditPropertyValueAction {
  private static final Key<Boolean> EDITABLE_PROPERTY_VALUE = Key.create("editable.property.value");

  @Nullable
  public static PsiElement getEditableElement(@Nonnull FoldRegion region) {
    PsiElement psiElement = EditorFoldingInfo.get(region.getEditor()).getPsiElement(region);
    return psiElement == null || psiElement.getUserData(EDITABLE_PROPERTY_VALUE) == null ? null : psiElement;
  }

  public static void registerFoldedElement(@Nonnull PsiElement element, @Nonnull Document document) {
    element.putUserData(EDITABLE_PROPERTY_VALUE, Boolean.TRUE);
    //EditPropertyValueTooltipManager.initializeForDocument(document);
  }
}
