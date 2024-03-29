public class Main {
  public static String escapeAndUnescapeSymbols(String s, StringBuilder builder) {
    boolean escaped = false;
    for (int i = 0; i < s.length(); i++) {
      final char ch = s.charAt(i);
      if (escaped) {
        if (ch=='n') builder.append('\n');
        if (<warning descr="Condition 'escaped' is always 'true'">escaped</warning>) break;
        escaped = false;
        continue;
      }
      if (ch == '\\') {
        escaped = true;
        continue;
      }
      builder.append(ch);
    }
    return builder.toString();
  }
}