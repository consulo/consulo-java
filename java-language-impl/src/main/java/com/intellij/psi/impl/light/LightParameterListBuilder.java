package com.intellij.psi.impl.light;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.intellij.lang.Language;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiManager;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParameterList;

/**
 * @author peter
 */
public class LightParameterListBuilder extends LightElement implements PsiParameterList {
  private final List<PsiParameter> myParameters = new ArrayList<PsiParameter>();
  private PsiParameter[] myCachedParameters;

  public LightParameterListBuilder(PsiManager manager, Language language) {
    super(manager, language);
  }

  public void addParameter(PsiParameter parameter) {
    myParameters.add(parameter);
    myCachedParameters = null;
  }

  @Override
  public String toString() {
    return "Light parameter list";
  }

  @Nonnull
  @Override
  public PsiParameter[] getParameters() {
    if (myCachedParameters == null) {
      if (myParameters.isEmpty()) {
        myCachedParameters = PsiParameter.EMPTY_ARRAY;
      }
      else {
        myCachedParameters = myParameters.toArray(new PsiParameter[myParameters.size()]);
      }
    }
    
    return myCachedParameters;
  }

  @Override
  public int getParameterIndex(PsiParameter parameter) {
    return myParameters.indexOf(parameter);
  }

  @Override
  public int getParametersCount() {
    return myParameters.size();
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitParameterList(this);
    }
  }

}
