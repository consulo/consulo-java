package com.intellij.java.impl.util.xml.impl;

import com.intellij.java.impl.util.xml.ui.PsiClassControl;
import com.intellij.java.impl.util.xml.ui.PsiClassTableCellEditor;
import com.intellij.java.impl.util.xml.ui.PsiTypeControl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.xml.dom.DomUIControlsProvider;
import consulo.xml.util.xml.ui.DomUIFactory;

/**
 * @author VISTALL
 * @since 14/12/2022
 */
@ExtensionImpl
public class JavaDomUIControlsProvider implements DomUIControlsProvider {
  @Override
  public void register(DomUIFactory factory) {
    factory.registerCustomControl(PsiClass.class, wrapper -> new PsiClassControl(wrapper, false));
    factory.registerCustomControl(PsiType.class, wrapper -> new PsiTypeControl(wrapper, false));

    factory.registerCustomCellEditor(PsiClass.class,
                                     element -> new PsiClassTableCellEditor(element.getManager().getProject(), element.getResolveScope()));

  }
}
