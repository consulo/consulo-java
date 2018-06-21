package consulo.java.jam;

import com.intellij.semantic.SemContributor;
import com.intellij.semantic.SemRegistrar;

/**
 * @author VISTALL
 * @since 14-Jan-17
 */
public class JamToSemContributor extends SemContributor {
  @Override
  public void registerSemProviders(SemRegistrar semRegistrar) {
    //TODO [VISTALL] looks like we need register MEMBER_META_KEY & ANNO_META_KEY & JAM_ELEMENT_KEY
  }
}
