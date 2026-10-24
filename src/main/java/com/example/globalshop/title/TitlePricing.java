package com.example.globalshop.title;

/**
 * What a title costs, character by character.
 *
 * <h2>The rule</h2>
 * A letter, a digit or a space is an ordinary character. Everything else — combining marks, symbols,
 * punctuation — is a special one, and costs more. That is deliberately the same distinction the eye
 * makes: ordinary text is what a title is for, and the decorative pieces are the thing being paid
 * for. It also has a second use, because {@link Character#isLetterOrDigit} is true for Chinese just
 * as it is for Latin, so "七个汉字" and seven letters are priced the same way.
 *
 * <p>The interesting case is the strikethrough the user asked about — the overlay drawn through a
 * character, like the one in "n̶o̶t̶". That is not one character with a line through it; it is the
 * letter followed by a combining mark, U+0336, which is a separate code point. So "n̶" is two
 * characters: one ordinary, one special. Counting it that way is why a title made of struck-through
 * text costs noticeably more than the same text plain, which is the intent.
 *
 * <p>Because a title is at most seven characters, the whole quote is cheap to compute and is shown
 * live in the window as it is typed.
 */
public final class TitlePricing {

    private TitlePricing() {
    }

    /**
     * A costed title.
     *
     * @param ordinary how many ordinary characters it has
     * @param special  how many special ones
     * @param total    the price, in the economy's smallest unit like every other price here
     */
    public record Quote(int ordinary, int special, long total) {

        /** How many characters the title has. A combining mark counts as one of them. */
        public int length() {
            return ordinary + special;
        }
    }

    /** Whether this code point is a special character, priced higher. */
    public static boolean isSpecial(int codePoint) {
        // A space is ordinary: titles like "Dark Knight" are meant to be reasonable.
        return codePoint != ' ' && !Character.isLetterOrDigit(codePoint);
    }

    /**
     * Prices a title at the server's own two prices.
     *
     * <p>Iterates by code point rather than by {@code char}, so an emoji or any other character
     * outside the basic plane is counted once instead of twice — a surrogate pair is two
     * {@code char}s and one character.
     *
     * <p>The prices are arguments rather than read from the config here, because the window prices
     * text on the client and must use the server's numbers rather than whatever is in the file on
     * that player's disk.
     */
    public static Quote quote(String text, long ordinaryPrice, long specialPrice) {
        int ordinary = 0;
        int special = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (isSpecial(codePoint)) {
                special++;
            } else {
                ordinary++;
            }
            i += Character.charCount(codePoint);
        }
        long total = (long) ordinary * ordinaryPrice + (long) special * specialPrice;
        return new Quote(ordinary, special, total);
    }
}
