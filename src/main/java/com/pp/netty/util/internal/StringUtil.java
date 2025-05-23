package com.pp.netty.util.internal;

import java.io.IOException;
import java.util.List;

/**
 * @Author: PP-jessica
 * @Description:netty自己封装的工具类，从源码复制过来，稍微注释了一些，不然要引入更多的源码
 */
public final class StringUtil {

    public static final String EMPTY_STRING = "";
    //public static final String NEWLINE = SystemPropertyUtil.get("line.separator", "\n");

    public static final char DOUBLE_QUOTE = '\"';
    public static final char COMMA = ',';
    public static final char LINE_FEED = '\n';
    public static final char CARRIAGE_RETURN = '\r';
    public static final char TAB = '\t';
    public static final char SPACE = 0x20;

    private static final String[] BYTE2HEX_PAD = new String[256];
    private static final String[] BYTE2HEX_NOPAD = new String[256];

    /**
     * 2 - Quote character at beginning and end.
     * 5 - Extra allowance for anticipated escape characters that may be added.
     */
    private static final int CSV_NUMBER_ESCAPE_CHARACTERS = 2 + 5;
    private static final char PACKAGE_SEPARATOR_CHAR = '.';

    static {
        // Generate the lookup table that converts a byte into a 2-digit hexadecimal integer.
        for (int i = 0; i < BYTE2HEX_PAD.length; i++) {
            String str = Integer.toHexString(i);
            BYTE2HEX_PAD[i] = i > 0xf ? str : ('0' + str);
            BYTE2HEX_NOPAD[i] = str;
        }
    }

    private StringUtil() {
        // Unused.
    }

    /**
     * Get the item after one char delim if the delim is found (else null).
     * This operation is a simplified and optimized
     * version of {@link String#split(String, int)}.
     */
    public static String substringAfter(String value, char delim) {
        int pos = value.indexOf(delim);
        if (pos >= 0) {
            return value.substring(pos + 1);
        }
        return null;
    }

    /**
     * Checks if two strings have the same suffix of specified length
     *
     * @param s   string
     * @param p   string
     * @param len length of the common suffix
     * @return true if both s and p are not null and both have the same suffix. Otherwise - false
     */
    public static boolean commonSuffixOfLength(String s, String p, int len) {
        return s != null && p != null && len >= 0 && s.regionMatches(s.length() - len, p, p.length() - len, len);
    }

    /**
     * Converts the specified byte value into a 2-digit hexadecimal integer.
     */
    public static String byteToHexStringPadded(int value) {
        return BYTE2HEX_PAD[value & 0xff];
    }

    /**
     * Converts the specified byte value into a 2-digit hexadecimal integer and appends it to the specified buffer.
     */
    public static <T extends Appendable> T byteToHexStringPadded(T buf, int value) {
        try {
            buf.append(byteToHexStringPadded(value));
        } catch (IOException e) {
            //PlatformDependent.throwException(e);
        }
        return buf;
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexStringPadded(byte[] src) {
        return toHexStringPadded(src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexStringPadded(byte[] src, int offset, int length) {
        return toHexStringPadded(new StringBuilder(length << 1), src, offset, length).toString();
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexStringPadded(T dst, byte[] src) {
        return toHexStringPadded(dst, src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexStringPadded(T dst, byte[] src, int offset, int length) {
        final int end = offset + length;
        for (int i = offset; i < end; i++) {
            byteToHexStringPadded(dst, src[i]);
        }
        return dst;
    }

    /**
     * Converts the specified byte value into a hexadecimal integer.
     */
    public static String byteToHexString(int value) {
        return BYTE2HEX_NOPAD[value & 0xff];
    }

    /**
     * Converts the specified byte value into a hexadecimal integer and appends it to the specified buffer.
     */
    public static <T extends Appendable> T byteToHexString(T buf, int value) {
        try {
            buf.append(byteToHexString(value));
        } catch (IOException e) {
            //PlatformDependent.throwException(e);
        }
        return buf;
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexString(byte[] src) {
        return toHexString(src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexString(byte[] src, int offset, int length) {
        return toHexString(new StringBuilder(length << 1), src, offset, length).toString();
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexString(T dst, byte[] src) {
        return toHexString(dst, src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexString(T dst, byte[] src, int offset, int length) {
        assert length >= 0;
        if (length == 0) {
            return dst;
        }

        final int end = offset + length;
        final int endMinusOne = end - 1;
        int i;

        // Skip preceding zeroes.
        for (i = offset; i < endMinusOne; i++) {
            if (src[i] != 0) {
                break;
            }
        }

        byteToHexString(dst, src[i++]);
        int remaining = end - i;
        toHexStringPadded(dst, src, i, remaining);

        return dst;
    }

    /**
     * Helper to decode half of a hexadecimal number from a string.
     * @param c The ASCII character of the hexadecimal number to decode.
     * Must be in the range {@code [0-9a-fA-F]}.
     * @return The hexadecimal value represented in the ASCII character
     * given, or {@code -1} if the character is invalid.
     */
    public static int decodeHexNibble(final char c) {
        // Character.digit() is not used here, as it addresses a larger
        // set of characters (both ASCII and full-width latin letters).
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - ('A' - 0xA);
        }
        if (c >= 'a' && c <= 'f') {
            return c - ('a' - 0xA);
        }
        return -1;
    }

    /**
     * Decode a 2-digit hex byte from within a string.
     */
    public static byte decodeHexByte(CharSequence s, int pos) {
        int hi = decodeHexNibble(s.charAt(pos));
        int lo = decodeHexNibble(s.charAt(pos + 1));
        if (hi == -1 || lo == -1) {
            throw new IllegalArgumentException(String.format(
                    "invalid hex byte '%s' at index %d of '%s'", s.subSequence(pos, pos + 2), pos, s));
        }
        return (byte) ((hi << 4) + lo);
    }

    /**
     * Decodes part of a string with <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     *
     * @param hexDump a {@link CharSequence} which contains the hex dump
     * @param fromIndex start of hex dump in {@code hexDump}
     * @param length hex string length
     */
    public static byte[] decodeHexDump(CharSequence hexDump, int fromIndex, int length) {
        if (length < 0 || (length & 1) != 0) {
            throw new IllegalArgumentException("length: " + length);
        }
        if (length == 0) {
            //return EmptyArrays.EMPTY_BYTES;
        }
        byte[] bytes = new byte[length >>> 1];
        for (int i = 0; i < length; i += 2) {
            bytes[i >>> 1] = decodeHexByte(hexDump, fromIndex + i);
        }
        return bytes;
    }

    /**
     * Decodes a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     */
    public static byte[] decodeHexDump(CharSequence hexDump) {
        return decodeHexDump(hexDump, 0, hexDump.length());
    }

    /**
     * The shortcut to {@link #simpleClassName(Class) simpleClassName(o.getClass())}.
     */
    public static String simpleClassName(Object o) {
        if (o == null) {
            return "null_object";
        } else {
            return simpleClassName(o.getClass());
        }
    }

    /**
     * Generates a simplified name from a {@link Class}.  Similar to {@link Class#getSimpleName()}, but it works fine
     * with anonymous classes.
     */
    public static String simpleClassName(Class<?> clazz) {
//        String className = checkNotNull(clazz, "clazz").getName();
        String className = clazz.getName();
        final int lastDotIdx = className.lastIndexOf(PACKAGE_SEPARATOR_CHAR);
//        if (lastDotIdx > -1) {
//            return className.substring(lastDotIdx + 1);
//        }
//        return className;
        return className.substring(lastDotIdx + 1);
    }

    /**
     * Escapes the specified value, if necessary according to
     * <a href="https://tools.ietf.org/html/rfc4180#section-2">RFC-4180</a>.
     *
     * @param value The value which will be escaped according to
     *              <a href="https://tools.ietf.org/html/rfc4180#section-2">RFC-4180</a>
     * @return {@link CharSequence} the escaped value if necessary, or the value unchanged
     */
    public static CharSequence escapeCsv(CharSequence value) {
        return escapeCsv(value, false);
    }

    /**
     * Escapes the specified value, if necessary according to
     * <a href="https://tools.ietf.org/html/rfc4180#section-2">RFC-4180</a>.
     *
     * @param value          The value which will be escaped according to
     *                       <a href="https://tools.ietf.org/html/rfc4180#section-2">RFC-4180</a>
     * @param trimWhiteSpace The value will first be trimmed of its optional white-space characters,
     *                       according to <a href="https://tools.ietf.org/html/rfc7230#section-7">RFC-7230</a>
     * @return {@link CharSequence} the escaped value if necessary, or the value unchanged
     */
    public static CharSequence escapeCsv(CharSequence value, boolean trimWhiteSpace) {
//        int length = checkNotNull(value, "value").length();
//        int start;
//        int last;
//        if (trimWhiteSpace) {
//            start = indexOfFirstNonOwsChar(value, length);
//            last = indexOfLastNonOwsChar(value, start, length);
//        } else {
//            start = 0;
//            last = length - 1;
//        }
//        if (start > last) {
//            return EMPTY_STRING;
//        }
//
//        int firstUnescapedSpecial = -1;
//        boolean quoted = false;
//        if (isDoubleQuote(value.charAt(start))) {
//            quoted = isDoubleQuote(value.charAt(last)) && last > start;
//            if (quoted) {
//                start++;
//                last--;
//            } else {
//                firstUnescapedSpecial = start;
//            }
//        }
//
//        if (firstUnescapedSpecial < 0) {
//            if (quoted) {
//                for (int i = start; i <= last; i++) {
//                    if (isDoubleQuote(value.charAt(i))) {
//                        if (i == last || !isDoubleQuote(value.charAt(i + 1))) {
//                            firstUnescapedSpecial = i;
//                            break;
//                        }
//                        i++;
//                    }
//                }
//            } else {
//                for (int i = start; i <= last; i++) {
//                    char c = value.charAt(i);
//                    if (c == LINE_FEED || c == CARRIAGE_RETURN || c == COMMA) {
//                        firstUnescapedSpecial = i;
//                        break;
//                    }
//                    if (isDoubleQuote(c)) {
//                        if (i == last || !isDoubleQuote(value.charAt(i + 1))) {
//                            firstUnescapedSpecial = i;
//                            break;
//                        }
//                        i++;
//                    }
//                }
//            }
//
//            if (firstUnescapedSpecial < 0) {
//                // Special characters is not found or all of them already escaped.
//                // In the most cases returns a same string. New string will be instantiated (via StringBuilder)
//                // only if it really needed. It's important to prevent GC extra load.
//                return quoted? value.subSequence(start - 1, last + 2) : value.subSequence(start, last + 1);
//            }
//        }
//
//        StringBuilder result = new StringBuilder(last - start + 1 + CSV_NUMBER_ESCAPE_CHARACTERS);
//        result.append(DOUBLE_QUOTE).append(value, start, firstUnescapedSpecial);
//        for (int i = firstUnescapedSpecial; i <= last; i++) {
//            char c = value.charAt(i);
//            if (isDoubleQuote(c)) {
//                result.append(DOUBLE_QUOTE);
//                if (i < last && isDoubleQuote(value.charAt(i + 1))) {
//                    i++;
//                }
//            }
//            result.append(c);
//        }
//        return result.append(DOUBLE_QUOTE);
        return null;
    }

    /**
     * Unescapes the specified escaped CSV field, if necessary according to
     * <a href="https://tools.ietf.org/html/rfc4180#section-2">RFC-4180</a>.
     *
     * @param value The escaped CSV field which will be unescaped according to
     *              <a href="https://tools.ietf.org/html/rfc4180#section-2">RFC-4180</a>
     * @return {@link CharSequence} the unescaped value if necessary, or the value unchanged
     */
//    public static CharSequence unescapeCsv(CharSequence value) {
//        int length = checkNotNull(value, "value").length();
//        if (length == 0) {
//            return value;
//        }
//        int last = length - 1;
//        boolean quoted = isDoubleQuote(value.charAt(0)) && isDoubleQuote(value.charAt(last)) && length != 1;
//        if (!quoted) {
//            validateCsvFormat(value);
//            return value;
//        }
//        StringBuilder unescaped = InternalThreadLocalMap.get().stringBuilder();
//        for (int i = 1; i < last; i++) {
//            char current = value.charAt(i);
//            if (current == DOUBLE_QUOTE) {
//                if (isDoubleQuote(value.charAt(i + 1)) && (i + 1) != last) {
//                    // Followed by a double-quote but not the last character
//                    // Just skip the next double-quote
//                    i++;
//                } else {
//                    // Not followed by a double-quote or the following double-quote is the last character
//                    throw newInvalidEscapedCsvFieldException(value, i);
//                }
//            }
//            unescaped.append(current);
//        }
//        return unescaped.toString();
//    }

    /**
     * Unescapes the specified escaped CSV fields according to
     * <a href="https://tools.ietf.org/html/rfc4180#section-2">RFC-4180</a>.
     *
     * @param value A string with multiple CSV escaped fields which will be unescaped according to
     *              <a href="https://tools.ietf.org/html/rfc4180#section-2">RFC-4180</a>
     * @return {@link List} the list of unescaped fields
     */
    public static List<CharSequence> unescapeCsvFields(CharSequence value) {
//        List<CharSequence> unescaped = new ArrayList<CharSequence>(2);
//        StringBuilder current = InternalThreadLocalMap.get().stringBuilder();
//        boolean quoted = false;
//        int last = value.length() - 1;
//        for (int i = 0; i <= last; i++) {
//            char c = value.charAt(i);
//            if (quoted) {
//                switch (c) {
//                    case DOUBLE_QUOTE:
//                        if (i == last) {
//                            // Add the last field and return
//                            unescaped.add(current.toString());
//                            return unescaped;
//                        }
//                        char next = value.charAt(++i);
//                        if (next == DOUBLE_QUOTE) {
//                            // 2 double-quotes should be unescaped to one
//                            current.append(DOUBLE_QUOTE);
//                            break;
//                        }
//                        if (next == COMMA) {
//                            // This is the end of a field. Let's start to parse the next field.
//                            quoted = false;
//                            unescaped.add(current.toString());
//                            current.setLength(0);
//                            break;
//                        }
//                        // double-quote followed by other character is invalid
//                        throw newInvalidEscapedCsvFieldException(value, i - 1);
//                    default:
//                        current.append(c);
//                }
//            } else {
//                switch (c) {
//                    case COMMA:
//                        // Start to parse the next field
//                        unescaped.add(current.toString());
//                        current.setLength(0);
//                        break;
//                    case DOUBLE_QUOTE:
//                        if (current.length() == 0) {
//                            quoted = true;
//                            break;
//                        }
//                        // double-quote appears without being enclosed with double-quotes
//                        // fall through
//                    case LINE_FEED:
//                        // fall through
//                    case CARRIAGE_RETURN:
//                        // special characters appears without being enclosed with double-quotes
//                        throw newInvalidEscapedCsvFieldException(value, i);
//                    default:
//                        current.append(c);
//                }
//            }
//        }
//        if (quoted) {
//            throw newInvalidEscapedCsvFieldException(value, last);
//        }
//        unescaped.add(current.toString());
//        return unescaped;
        return null;
    }

    /**
     * Validate if {@code value} is a valid csv field without double-quotes.
     *
     * @throws IllegalArgumentException if {@code value} needs to be encoded with double-quotes.
     */
    private static void validateCsvFormat(CharSequence value) {
        int length = value.length();
        for (int i = 0; i < length; i++) {
            switch (value.charAt(i)) {
                case DOUBLE_QUOTE:
                case LINE_FEED:
                case CARRIAGE_RETURN:
                case COMMA:
                    // If value contains any special character, it should be enclosed with double-quotes
                    throw newInvalidEscapedCsvFieldException(value, i);
                default:
            }
        }
    }

    private static IllegalArgumentException newInvalidEscapedCsvFieldException(CharSequence value, int index) {
        return new IllegalArgumentException("invalid escaped CSV field: " + value + " index: " + index);
    }

    /**
     * Get the length of a string, {@code null} input is considered {@code 0} length.
     */
    public static int length(String s) {
        return s == null ? 0 : s.length();
    }

    /**
     * Determine if a string is {@code null} or {@link String#isEmpty()} returns {@code true}.
     */
    public static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * Find the index of the first non-white space character in {@code s} starting at {@code offset}.
     *
     * @param seq    The string to search.
     * @param offset The offset to start searching at.
     * @return the index of the first non-white space character or &lt;{@code 0} if none was found.
     */
    public static int indexOfNonWhiteSpace(CharSequence seq, int offset) {
        for (; offset < seq.length(); ++offset) {
            if (!Character.isWhitespace(seq.charAt(offset))) {
                return offset;
            }
        }
        return -1;
    }

    /**
     * Determine if {@code c} lies within the range of values defined for
     * <a href="http://unicode.org/glossary/#surrogate_code_point">Surrogate Code Point</a>.
     *
     * @param c the character to check.
     * @return {@code true} if {@code c} lies within the range of values defined for
     * <a href="http://unicode.org/glossary/#surrogate_code_point">Surrogate Code Point</a>. {@code false} otherwise.
     */
    public static boolean isSurrogate(char c) {
        return c >= '\uD800' && c <= '\uDFFF';
    }

    private static boolean isDoubleQuote(char c) {
        return c == DOUBLE_QUOTE;
    }

    /**
     * Determine if the string {@code s} ends with the char {@code c}.
     *
     * @param s the string to test
     * @param c the tested char
     * @return true if {@code s} ends with the char {@code c}
     */
    public static boolean endsWith(CharSequence s, char c) {
        int len = s.length();
        return len > 0 && s.charAt(len - 1) == c;
    }

    /**
     * Trim optional white-space characters from the specified value,
     * according to <a href="https://tools.ietf.org/html/rfc7230#section-7">RFC-7230</a>.
     *
     * @param value the value to trim
     * @return {@link CharSequence} the trimmed value if necessary, or the value unchanged
     */
    public static CharSequence trimOws(CharSequence value) {
        final int length = value.length();
        if (length == 0) {
            return value;
        }
        int start = indexOfFirstNonOwsChar(value, length);
        int end = indexOfLastNonOwsChar(value, start, length);
        return start == 0 && end == length - 1 ? value : value.subSequence(start, end + 1);
    }

    /**
     * @return {@code length} if no OWS is found.
     */
    private static int indexOfFirstNonOwsChar(CharSequence value, int length) {
        int i = 0;
        while (i < length && isOws(value.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * @return {@code start} if no OWS is found.
     */
    private static int indexOfLastNonOwsChar(CharSequence value, int start, int length) {
        int i = length - 1;
        while (i > start && isOws(value.charAt(i))) {
            i--;
        }
        return i;
    }

    private static boolean isOws(char c) {
        return c == SPACE || c == TAB;
    }
}

