package com.siyeh.ipp.opassign;

import com.intellij.java.impl.ipp.opassign.ReplaceOperatorAssignmentWithAssignmentIntention;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ReplaceOperatorAssignmentWithAssignmentIntention
 */
public abstract class ReplaceOperatorAssignmentWithAssignmentIntentionTest extends IPPTestCase {
  public void testOperatorAssignment1() { doTest(); }
  public void testDoubleOpAssign() { doTest(); }
  public void testStringOpAssign() { doTest(); }
  public void testByteOpAssign() { doTest(); }
  public void testPrecedence() { doTest(); }
  public void testPolyadicAssignment() { doTest(IntentionPowerPackBundle.message("replace.operator.assignment.with.assignment.intention.name", "*=")); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.operator.assignment.with.assignment.intention.name", "+=");
  }

  @Override
  protected String getRelativePath() {
    return "opassign/assignment";
  }
}
