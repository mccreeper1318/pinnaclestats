package org.pinnaclesmp.pinnaclestats;

import java.util.*;

public final class MiniJson {
    private MiniJson() {}

    public static Object parse(String json) {
        return new Parser(json).parseValue();
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
        private int pos;

        Parser(String s) { this.s = s == null ? "" : s; }

        Object parseValue() {
            skipWhitespace();
            if (pos >= s.length()) throw error("Unexpected end of JSON");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
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

        private Map<String, Object> parseObject() {
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
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    pos++;
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            ArrayList<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
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
                            out.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw error("Bad escape: " + e);
                    }
                } else {
                    out.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private Number parseNumber() {
            int start = pos;
            if (peek('-')) pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                decimal = true;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            String n = s.substring(start, pos);
            if (decimal) return Double.parseDouble(n);
            return Long.parseLong(n);
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

        private RuntimeException error(String message) {
            return new IllegalArgumentException(message + " at position " + pos);
        }
    }
}
