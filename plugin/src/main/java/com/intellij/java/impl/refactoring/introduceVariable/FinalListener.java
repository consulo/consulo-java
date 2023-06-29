package com.intellij.java.impl.refactoring.introduceVariable;

import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiVariable;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.completion.lookup.LookupEx;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.logging.Logger;

/**
* User: anna
*/
public class FinalListener {
  private final Editor myEditor;
  private static final Logger LOG = Logger.getInstance(FinalListener.class);

  public FinalListener(Editor editor) {
    myEditor = editor;
  }

  public void perform(final boolean generateFinal, PsiVariable variable) {
    perform(generateFinal, PsiModifier.FINAL, variable);
  }

  public void perform(final boolean generateFinal, final String modifier, final PsiVariable variable) {
    final Document document = myEditor.getDocument();
    LOG.assertTrue(variable != null);
    final PsiModifierList modifierList = variable.getModifierList();
    LOG.assertTrue(modifierList != null);
    final int textOffset = modifierList.getTextOffset();

    final Runnable runnable = new Runnable() {
      public void run() {
        if (generateFinal) {
          final PsiTypeElement typeElement = variable.getTypeElement();
          final int typeOffset = typeElement != null ? typeElement.getTextOffset() : textOffset;
          document.insertString(typeOffset, modifier + " ");
        }
        else {
          final int idx = modifierList.getText().indexOf(modifier);
          document.deleteString(textOffset + idx, textOffset + idx + modifier.length() + 1);
        }
      }
    };
    final LookupEx lookup = LookupManager.getActiveLookup(myEditor);
    if (lookup != null) {
      lookup.performGuardedChange(runnable);
    } else {
      runnable.run();
    }
    PsiDocumentManager.getInstance(variable.getProject()).commitDocument(document);
  }
}
