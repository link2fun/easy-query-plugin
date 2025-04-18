package com.easy.query.plugin.core.util;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;

import static java.lang.Character.isJavaIdentifierStart;
/*
 * Original class is here:
 * https://github.com/hibernate/hibernate-orm/blob/a30635f14ae272fd63a653f9a9e1a9aeb390fad4/hibernate-core/src/main/java/org/hibernate/engine/jdbc/internal/BasicFormatterImpl.java
 *
 * it imported org.hibernate.internal.util.StringHelper, but it doesn't really need that.
 */

/**
 * Performs formatting of basic SQL statements (DML + query).
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public class BasicFormatter2 {

    private static final Set<String> NON_FUNCTION_NAMES = Set.of(
        "select", "from", "on", "set", "and", "or", "where", "having", "by", "using"
    );

    private static final String INDENT_STRING = "    ";
    private static final String INITIAL = System.lineSeparator() + INDENT_STRING;

    public String format(String source) {
        return new FormatProcess(source).perform();
    }

    private static class FormatProcess {
        public static final String WHITESPACE = " \n\r\f\t";
        boolean beginLine = true;
        boolean afterBeginBeforeEnd;
        boolean afterByOrSetOrFromOrSelect;
        int afterOn;
        boolean afterBetween;
        boolean afterInsert;
        int inFunction;
        int parensSinceSelect;
        int valuesParenCount;
        private final LinkedList<Integer> parenCounts = new LinkedList<>();
        private final LinkedList<Boolean> afterByOrFromOrSelects = new LinkedList<>();

        int indent = 1;

        StringBuilder result = new StringBuilder();
        StringTokenizer tokens;
        String lastToken;
        String token;
        String lcToken;

        public FormatProcess(String sql) {
            assert sql != null : "SQL to format should not be null";

            tokens = new StringTokenizer(
                sql,
                "()+*/-=<>'`\"[]," + WHITESPACE,
                true
            );
        }

        public String perform() {

            result.append(INITIAL);

            while (tokens.hasMoreTokens()) {
                token = tokens.nextToken();

                if ("-".equals(token) && result.toString().endsWith("-")) {
                    do {
                        result.append(token);
                        token = tokens.nextToken();
                    }
                    while (!"\n".equals(token) && tokens.hasMoreTokens());
                    result.append("\n");
                }

                lcToken = token.toLowerCase(Locale.ROOT);
                switch (lcToken) {
                    case "'":
                    case "`":
                    case "\"":
                        appendUntilToken(lcToken);
                        misc();
                        break;
                    // SQL Server uses "[" and "]" to escape reserved words
                    // see SQLServerDialect.openQuote and SQLServerDialect.closeQuote
                    case "[":
                        appendUntilToken("]");
                        misc();
                        break;

                    case ",":
                        comma();
                        break;

                    case "(":
                        openParen();
                        break;
                    case ")":
                        closeParen();
                        break;

                    case "for":
                        forUpdate();
                        break;

                    case "select":
                        select();
                        break;
                    case "update":
                        if ("for".equals(lastToken)) {
                            out();
                            break;
                        }
                        // else fall through
                    case "insert":
                    case "delete":
                    case "merge":
                        updateOrInsertOrDelete();
                        break;

                    case "values":
                        values();
                        break;

                    case "on":
                        on();
                        break;

                    case "between":
                        afterBetween = true;
                        misc();
                        break;

                    //TODO: detect when 'left', 'right' are function names
                    case "left":
                    case "right":
                    case "full":
                    case "inner":
                    case "outer":
                    case "cross":
                    case "group":
                    case "order":
                    case "returning":
                    case "using":
                        beginNewClause();
                        break;

                    case "from":
                        from();
                        break;
                    case "where":
                    case "set":
                    case "having":
                    case "by":
                    case "join":
                    case "into":
                    case "union":
                    case "intersect":
                    case "offset":
                    case "limit":
                    case "fetch":
                        endNewClause();
                        break;

                    case "case":
                        beginCase();
                        break;
                    case "end":
                        endCase();
                        break;

                    case "when":
                    case "else":
                        when();
                        break;
                    case "then":
                        then();
                        break;
                    case "and":
                        and();
                        break;
                    case "or":
                        or();
                        break;
                    default:
                        if (isWhitespace(token)) {
                            white();
                        } else {
                            misc();
                        }
                }

                if (!isWhitespace(token)) {
                    lastToken = lcToken;
                }

            }
            return result.toString();
        }

        private void forUpdate() {
            if (inFunction == 0) {
                decrementIndent();
                newline();
                out();
                incrementIndent();
                newline();
            } else {
                misc();
            }
        }

        private void or() {
            logical();
        }

        private void and() {
            if (afterBetween) {
                misc();
                afterBetween = false;
            } else {
                logical();
            }
        }

        private void from() {
            if (inFunction == 0) {
                endNewClause();
            } else {
                misc();
            }
        }

        private void comma() {
            if (afterByOrSetOrFromOrSelect && inFunction == 0) {
                commaAfterByOrFromOrSelect();
            }
//			else if ( afterOn && inFunction==0 ) {
//				commaAfterOn();
//			}
            else {
                misc();
            }
        }

        private void then() {
            incrementIndent();
            newline();
            misc();
        }

        private void when() {
            decrementIndent();
            newline();
            out();
            beginLine = false;
            afterBeginBeforeEnd = true;
        }

//		private void commaAfterOn() {
//			out();
//			decrementIndent();
//			newline();
//			afterOn = false;
//			afterByOrSetOrFromOrSelect = true;
//		}

        private void commaAfterByOrFromOrSelect() {
            out();
            newline();
        }

        private void logical() {
            newline();
            out();
            beginLine = false;
        }

        private void endCase() {
            afterBeginBeforeEnd = false;
            decrementIndent();
            decrementIndent();
            logical();
        }

        private void on() {
            if (afterOn == 0) {
                incrementIndent();
            } else if (afterOn == 1) {
                // ad hoc, but gives a nice result
                decrementIndent();
            }
            afterOn++;
            newline();
            out();
            beginLine = false;
        }

        private void beginCase() {
            out();
            beginLine = false;
            incrementIndent();
            incrementIndent();
            afterBeginBeforeEnd = true;
        }

        private void misc() {
            out();
            if (afterInsert && inFunction == 0) {
                newline();
                afterInsert = false;
            } else {
                beginLine = false;
            }
        }

        private void white() {
            if (!beginLine) {
                result.append(" ");
            }
        }

        private void updateOrInsertOrDelete() {
            if (indent > 1) {
                //probably just the insert SQL function
                out();
            } else {
                out();
                incrementIndent();
                beginLine = false;
                if ("update".equals(lcToken)) {
                    newline();
                }
                if ("insert".equals(lcToken)) {
                    afterInsert = true;
                }
            }
        }

        private void select() {
//			if ( parensSinceSelect > 0 ) {
//				newline();
//				incrementIndent();
//				out();
//			}
//			else {
//				out();
//				incrementIndent();
//			}
            out();
            incrementIndent();
            newline();
            parenCounts.addLast(parensSinceSelect);
            afterByOrFromOrSelects.addLast(afterByOrSetOrFromOrSelect);
            parensSinceSelect = 0;
            afterByOrSetOrFromOrSelect = true;
        }

        private void out() {
            if (result.charAt(result.length() - 1) == ',') {
                result.append(" ");
            }
            result.append(token);
        }

        private void endNewClause() {
            if (!afterBeginBeforeEnd) {
                decrementIndent();
                if (afterOn == 1) {
                    decrementIndent();
                }
                if (afterOn > 0) {
                    afterOn = 0;
                }
                newline();
            }
            out();
            if (!"union".equals(lcToken) && !"intersect".equals(lcToken)) {
                incrementIndent();
            }
            newline();
            afterBeginBeforeEnd = false;
            afterByOrSetOrFromOrSelect = "by".equals(lcToken)
                || "set".equals(lcToken)
                || "from".equals(lcToken);
        }

        private void beginNewClause() {
            if (!afterBeginBeforeEnd) {
                if (afterOn == 1) {
                    decrementIndent();
                }
                if (afterOn > 0) {
                    afterOn = 0;
                }
                decrementIndent();
                newline();
            }
            out();
            beginLine = false;
            afterBeginBeforeEnd = true;
        }

        private void values() {
            if (parensSinceSelect == 0) {
                if (!afterBeginBeforeEnd) {
                    decrementIndent();
                }
                newline();
                out();
                incrementIndent();
                newline();
                valuesParenCount = parensSinceSelect + 1;
            } else {
                out();
            }
        }

        private void closeParen() {
            if (parensSinceSelect == 0) {
                decrementIndent();
                parensSinceSelect = parenCounts.removeLast();
                afterByOrSetOrFromOrSelect = afterByOrFromOrSelects.removeLast();
            } else if (valuesParenCount == parensSinceSelect) {
                valuesParenCount = 0;
                if (afterBeginBeforeEnd) {
                    decrementIndent();
                }
            }
            parensSinceSelect--;
            if (inFunction > 0) {
                // this should come first,
                // because we increment
                // inFunction for every
                // opening paren from the
                // first one after the
                // function name
                inFunction--;
                out();
            } else if (afterOn > 0) {
                out();
            } else {
                if (!afterByOrSetOrFromOrSelect) {
                    decrementIndent();
                    newline();
                }
                out();
            }
            beginLine = false;
        }

        private void openParen() {
            if (isFunctionName(lastToken) || inFunction > 0) {
                inFunction++;
            }
            beginLine = false;
            if (afterOn > 0 || inFunction > 0) {
                out();
            } else {
                out();
                if (!afterByOrSetOrFromOrSelect) {
                    incrementIndent();
                    newline();
                    beginLine = true;
                }
            }
            parensSinceSelect++;
        }

        private void incrementIndent() {
            indent++;
        }

        private void decrementIndent() {
            if (indent > 0) {
                indent--;
            }
        }

        private static boolean isFunctionName(String tok) {
            if (tok == null || tok.isEmpty()) {
                return false;
            } else {
                final char begin = tok.charAt(0);
                final boolean isIdentifier = isJavaIdentifierStart(begin) || '"' == begin;
                return isIdentifier && !NON_FUNCTION_NAMES.contains(tok);
            }
        }

        private static boolean isWhitespace(String token) {
            return WHITESPACE.contains(token);
        }

        private void newline() {
            result.append(System.lineSeparator())
                .append(INDENT_STRING.repeat(indent));
            beginLine = true;
        }

        private void appendUntilToken(String stopToken) {
            final StringBuilder quoted = new StringBuilder(this.token);
            String t;
            do {
                t = tokens.nextToken();
                quoted.append(t);
            }
            while (!stopToken.equals(t) && tokens.hasMoreTokens());
            this.token = quoted.toString();
            lcToken = this.token;
        }
    }
}
