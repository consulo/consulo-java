package consulo.java.jam.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.sem.SemContributor;
import consulo.language.sem.SemRegistrar;

/**
 * @author VISTALL
 * @since 14-Jan-17
 */
@ExtensionImpl
public class JamToSemContributor extends SemContributor {
  @Override
  public void registerSemProviders(SemRegistrar semRegistrar) {
    //TODO [VISTALL] looks like we need register MEMBER_META_KEY & ANNO_META_KEY & JAM_ELEMENT_KEY
  }
}
