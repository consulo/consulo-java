package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import consulo.logging.Logger;
import consulo.ide.impl.idea.openapi.util.Comparing;
import consulo.document.util.ProperTextRange;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.NonCodeUsageInfo;
import consulo.usage.UsageInfo;
import consulo.language.util.IncorrectOperationException;

import java.util.*;

public class CommonMoveUtil {

  private CommonMoveUtil() {
  }

  private static final Logger LOG = Logger.getInstance(CommonMoveUtil.class);

  public static NonCodeUsageInfo[] retargetUsages(final UsageInfo[] usages, final Map<PsiElement, PsiElement> oldToNewElementsMapping)
      throws IncorrectOperationException {
    Arrays.sort(usages, new Comparator<UsageInfo>() {
      @Override
      public int compare(UsageInfo o1, UsageInfo o2) {
        final VirtualFile file1 = o1.getVirtualFile();
        final VirtualFile file2 = o2.getVirtualFile();
        if (Comparing.equal(file1, file2)) {
          final ProperTextRange rangeInElement1 = o1.getRangeInElement();
          final ProperTextRange rangeInElement2 = o2.getRangeInElement();
          if (rangeInElement1 != null && rangeInElement2 != null) {
            return rangeInElement2.getStartOffset() - rangeInElement1.getStartOffset();
          }
          return 0;
        }
        if (file1 == null) return -1;
        if (file2 == null) return 1;
        return Comparing.compare(file1.getPath(), file2.getPath());
      }
    });
    List<NonCodeUsageInfo> nonCodeUsages = new ArrayList<NonCodeUsageInfo>();
    for (UsageInfo usage : usages) {
      if (usage instanceof NonCodeUsageInfo) {
        nonCodeUsages.add((NonCodeUsageInfo) usage);
      } else if (usage instanceof MoveRenameUsageInfo) {
        final MoveRenameUsageInfo moveRenameUsage = (MoveRenameUsageInfo) usage;
        final PsiElement oldElement = moveRenameUsage.getReferencedElement();
        final PsiElement newElement = oldToNewElementsMapping.get(oldElement);
        LOG.assertTrue(newElement != null);
        final PsiReference reference = moveRenameUsage.getReference();
        if (reference != null) {
          try {
            reference.bindToElement(newElement);
          } catch (IncorrectOperationException e) {//
          }
        }
      }
    }
    return nonCodeUsages.toArray(new NonCodeUsageInfo[nonCodeUsages.size()]);
  }
}
