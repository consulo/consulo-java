package com.intellij.java.analysis.impl.codeInspection.dataFlow;

/**
 * @author VISTALL
 * @since 2023-03-19
 */
public class DataFlowInspectionStateBase {
    public boolean SUGGEST_NULLABLE_ANNOTATIONS;
    public boolean DONT_REPORT_TRUE_ASSERT_STATEMENTS;
    public boolean TREAT_UNKNOWN_MEMBERS_AS_NULLABLE;
    public boolean IGNORE_ASSERT_STATEMENTS;
    public boolean REPORT_CONSTANT_REFERENCE_VALUES = true;
    public boolean REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = true;
    public boolean REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = true;
    public boolean REPORT_UNSOUND_WARNINGS = true;
}
