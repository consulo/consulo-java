import javax.annotation.Nullable;

interface PsiElement {
  @Nullable
  <T> T getCopyableUserData(Key<T> key);
}