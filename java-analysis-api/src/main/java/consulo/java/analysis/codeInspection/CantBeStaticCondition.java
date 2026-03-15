package consulo.java.analysis.codeInspection;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.language.psi.PsiElement;

/**
 * @author VISTALL
 * @since 01-Sep-22
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface CantBeStaticCondition {
  boolean cantBeStatic(PsiElement element);
}
