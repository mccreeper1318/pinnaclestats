package io.github.mccreeper1318.pinnaclestats;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MiniJson {
    static final int MAX_INPUT_BYTES = 16 * 1024 * 1024;
    static final int MAX_NESTING_DEPTH = 64;

    private MiniJson() {}

    public static Object parse(String json) {
        return parse(json, MAX_INPUT_BYTES, MAX_NESTING_DEPTH);
    }

    static Object parse(String json, int maxInputBytes, int maxNestingDepth) {
        if (maxInputBytes < 1) throw new IllegalArgumentException("Maximum JSON input size must be positive.");
        if (maxNestingDepth < 1) throw new IllegalArgumentException("Maximum JSON nesting depth must be positive.");

        String input = json == null ? "" : json;
        requireInputSize(input.getBytes(StandardCharsets.UTF_8).length, maxInputBytes);
        return new Parser(input, maxNestingDepth).parseDocument();
    }

    static void requireInputSize(long inputBytes) {
        requireInputSize(inputBytes, MAX_INPUT_BYTES);
    }

    private static void requireInputSize(long inputBytes, int maxInputBytes) {
        if (inputBytes > maxInputBytes) {
            throw new IllegalArgumentException(
                    "JSON input exceeds maximum size of " + maxInputBytes + " bytes (received " + inputBytes + ")."
            );
        }
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(out, s);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) out.append(',');
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (i > 0) out.append(',');
                writeValue(out, java.lang.reflect.Array.get(value, i));
            }
            out.append(']');
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String s;
        private final int maxNestingDepth;
        private int pos;

        Parser(String s, int maxNestingDepth) {
            this.s = s;
            this.maxNestingDepth = maxNestingDepth;
        }

        Object parseDocument() {
            Object value = parseValue(0);
            skipWhitespace();
            if (pos != s.length()) {
                throw error("Trailing content after JSON document");
            }
            return value;
        }

        private Object parseValue(int depth) {
            skipWhitespace();
            if (pos >= s.length()) throw error("Unexpected end of JSON");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> parseObject(nextDepth(depth));
                case '[' -> parseArray(nextDepth(depth));
                case '"' -> parseString();
                case 't' -> { expect("true"); yield Boolean.TRUE; }
                case 'f' -> { expect("false"); yield Boolean.FALSE; }
                case 'n' -> { expect("null"); yield null; }
                default -> {
                    if (c == '-' || Character.isDigit(c)) yield parseNumber();
                    throw error("Unexpected character: " + c);
                }
            };
        }

        private int nextDepth(int currentDepth) {
            int next = currentDepth + 1;
            if (next > maxNestingDepth) {
                throw error("JSON nesting exceeds maximum depth of " + maxNestingDepth);
            }
            return next;
        }

        private Map<String, Object> parseObject(int depth) {
            expect('{');
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue(depth);
                map.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    pos++;
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> parseArray(int depth) {
            expect('[');
            ArrayList<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue(depth));
                skipWhitespace();
                if (peek(']')) {
                    pos++;
                    return list;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return out.toString();
                if (c == '\\') {
                    if (pos >= s.length()) throw error("Bad escape");
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (pos + 4 > s.length()) throw error("Bad unicode escape");
                            String hex = s.substring(pos, pos + 4);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                throw error("Bad unicode escape");
                            }
                            pos += 4;
                        }
                        default -> throw error("Bad escape: " + e);
                    }
                } else {
                    if (c < 0x20) throw error("Unescaped control character in string");
                    out.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private Number parseNumber() {
            int start = pos;
            if (peek('-')) pos++;

            if (peek('0')) {
                pos++;
            } else {
                int digitStart = pos;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                if (digitStart == pos) throw error("Invalid number");
            }

            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                pos++;
                int fractionStart = pos;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                if (fractionStart == pos) throw error("Invalid number");
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                decimal = true;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                int exponentStart = pos;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                if (exponentStart == pos) throw error("Invalid number");
            }

            String n = s.substring(start, pos);
            try {
                if (decimal) return Double.parseDouble(n);
                return Long.parseLong(n);
            } catch (NumberFormatException ex) {
                throw error("Invalid number");
            }
        }

        private void expect(String text) {
            if (!s.startsWith(text, pos)) throw error("Expected " + text);
            pos += text.length();
        }

        private void expect(char c) {
            skipWhitespace();
            if (pos >= s.length() || s.charAt(pos) != c) throw error("Expected " + c);
            pos++;
        }

        private boolean peek(char c) {
            return pos < s.length() && s.charAt(pos) == c;
        }

        private void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++;
                else break;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + pos);
        }
    }
}
