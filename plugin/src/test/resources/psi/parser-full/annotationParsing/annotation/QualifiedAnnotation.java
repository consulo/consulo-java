import org.jspecify.annotations.Nullable;

interface PsiElement {
  @Nullable
  <T> T getCopyableUserData(Key<T> key);
}