package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import consulo.document.util.ProperTextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.NonCodeUsageInfo;
import consulo.usage.UsageInfo;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;

import java.util.*;

public class CommonMoveUtil {

  private CommonMoveUtil() {
  }

  private static final Logger LOG = Logger.getInstance(CommonMoveUtil.class);

  public static NonCodeUsageInfo[] retargetUsages(UsageInfo[] usages, Map<PsiElement, PsiElement> oldToNewElementsMapping)
      throws IncorrectOperationException {
    Arrays.sort(usages, new Comparator<UsageInfo>() {
      @Override
      public int compare(UsageInfo o1, UsageInfo o2) {
        VirtualFile file1 = o1.getVirtualFile();
        VirtualFile file2 = o2.getVirtualFile();
        if (Comparing.equal(file1, file2)) {
          ProperTextRange rangeInElement1 = o1.getRangeInElement();
          ProperTextRange rangeInElement2 = o2.getRangeInElement();
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
        MoveRenameUsageInfo moveRenameUsage = (MoveRenameUsageInfo) usage;
        PsiElement oldElement = moveRenameUsage.getReferencedElement();
        PsiElement newElement = oldToNewElementsMapping.get(oldElement);
        LOG.assertTrue(newElement != null);
        PsiReference reference = moveRenameUsage.getReference();
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
