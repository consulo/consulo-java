package consulo.java.jam.impl;

import com.intellij.jam.JamClassGenerator;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElementRef;
import jakarta.inject.Singleton;

import java.util.function.Function;

/**
 * @author VISTALL
 * @since 14-Jan-17
 */
@Singleton
@ServiceImpl
public class JamClassGeneratorImpl implements JamClassGenerator {

  @Override
  public <T> Function<PsiElementRef, T> generateJamElementFactory(Class<T> aClass) {
    return new JamElementFactory<>(aClass);
  }
}
