package me.megamichiel.animationlib.placeholder;

import me.megamichiel.animationlib.Nagger;
import me.megamichiel.animationlib.placeholder.ctx.ParsingContext;
import me.megamichiel.animationlib.util.StringReader;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringBundle extends ArrayList<Object> implements CtxPlaceholder<String>, Cloneable {

    public static final char BOX = '\u2588';

    private static final long serialVersionUID = 5196073861968616865L;

    private static Function<String, IPlaceholder<String>> adapter;

    public static void setAdapter(Function<String, IPlaceholder<String>> adapter) {
        if (StringBundle.adapter == null) StringBundle.adapter = adapter;
    }

    public static IPlaceholder<String> createPlaceholder(String identifier) {
        return adapter.apply(identifier);
    }

    private final Nagger nagger;
    
    public StringBundle(Nagger nagger, String value) {
        this.nagger = nagger;
        add(value);
    }
    
    public StringBundle(Nagger nagger) {
        this.nagger = nagger;
    }

    public StringBundle(Nagger nagger, int capacity) {
        super(capacity);
        this.nagger = nagger;
        ensureCapacity(capacity);
    }

    public StringBundle(Nagger nagger, Collection<?> c) {
        super(c);
        this.nagger = nagger;
    }

    /**
     * Returns whether this StringBundle contains anything other than Strings
     */
    public boolean containsPlaceholders() {
        for (Object o : this)
            if (!(o instanceof String))
                return true;
        return false;
    }

    /**
     * Replaces <i>query</i> with a placeholder. (case-insensitive)<br/>
     * <i>Since: 1.1.0</i>
     *
     * @param query the text to find
     * @param placeholder the placeholder to replace the text with
     */
    public void replace(String query, IPlaceholder<?> placeholder) {
        query = query.toLowerCase(Locale.US);
        for (int i = 0; i < size(); i++) {
            if (get(i) instanceof String) {
                String val = (String) get(i);
                List<Object> result = new ArrayList<>();
                for (int index; (index = val.toLowerCase(Locale.US).indexOf(query)) > -1;
                     val = val.substring(index + query.length())) {
                    if (index > 0) result.add(val.substring(0, index));
                    result.add(placeholder);
                }
                if (!result.isEmpty()) {
                    remove(i);
                    if (!val.isEmpty()) result.add(val);
                    addAll(i, result);
                }
            }
        }
    }

    /**
     * Replaces all matches for <i>pattern</i> with an IPlaceholder retrieved by <i>function</i><br/>
     * <i>Since: 1.1.1</i>
     *
     * @param pattern the pattern to find text with
     * @param function the Function that creates an IPlaceholder from a Matcher
     */
    public void replace(Pattern pattern, Function<? super Matcher, ? extends IPlaceholder<?>> function) {
        for (int i = 0; i < size(); i++) {
            if (get(i) instanceof String) {
                String val = (String) get(i);
                Matcher matcher = pattern.matcher(val);
                if (matcher.find()) {
                    List<Object> result = new ArrayList<>();
                    int lastEnd = 0;
                    for (boolean found = true; found; lastEnd = matcher.end(), found = matcher.find()) {
                        if (matcher.start() > 0)
                            result.add(val.substring(lastEnd, matcher.start()));
                        result.add(function.apply(matcher));
                    }
                    if (lastEnd < val.length())
                        result.add(val.substring(lastEnd));
                    remove(i);
                    addAll(i, result);
                }
            }
        }
    }

    @Override
    public String invoke(Nagger nagger, Object who, PlaceholderContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (Object o : this) {
            if (o instanceof IPlaceholder) {
                try {
                    sb.append(((IPlaceholder) o).invoke(nagger, who, ctx));
                } catch (NullPointerException ex) {
                    if (who == null) sb.append("<requires_player>");
                    else ex.printStackTrace();
                }
            } else sb.append(String.valueOf(o));
        }
        return sb.toString();
    }

    /**
     * Invokes this StringBundle's placeholders with the specified <i>PlaceholderContext</i>
     *
     * @param player the player to invoke the placeholders with
     * @param context the context to save values to
     * @return a String containing all invoked IPlaceholders
     */
    public String toString(Object player, PlaceholderContext context) {
        return invoke(nagger, player, context);
    }

    /**
     * Invokes {@link #invoke(Nagger, Object)} using the Nagger given in this class' constructor<br/>
     *
     *
     * @param player the player to invoke the placeholders with
     * @return a String, containing the result of the placeholders
     */
    public String toString(Object player) {
        return invoke(nagger, player);
    }

    /**
     * Attemps to return this value as a constant String IPlaceholder.<br/>
     *
     * @return a constant String placeholder if {@link #containsPlaceholders()} returns false.
     * Returns this StringBundle instance otherwise
     */
    public IPlaceholder<String> tryCache() {
        if (!containsPlaceholders()) return IPlaceholder.constant(toString(null));
        return this;
    }

    /**
     * Colors all ampersands (&) in each String of this StringBundle<br/>
     * 
     *
     * @return this StringBundle instance
     */
    public StringBundle colorAmpersands() {
        for (int i = 0, size = size(); i < size; i++)
            if (get(i) instanceof String)
                set(i, colorAmpersands((String) get(i)));
        return this;
    }

    public static String colorAmpersands(String str) {
        char[] chars = str.toCharArray();

        for (int pos = 0; pos < chars.length - 1; pos++) {
            if (chars[pos] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(chars[pos + 1]) > -1) {
                chars[pos++] = '\u00A7';
                chars[pos] = Character.toString(chars[pos]).toLowerCase(Locale.ENGLISH).charAt(0);
            }
        }
        return new String(chars);
    }

    /**
     * Creates an array of StringBundles from an array of Strings<br/>
     * This method does not parse the strings, for that see {@link #parse(Nagger, String...)}<br/>
     * 
     *
     * @param nagger the Nagger to report errors to
     * @param array the array of strings to transform
     */
    public static StringBundle[] fromArray(Nagger nagger, String... array) {
        StringBundle[] bundles = new StringBundle[array.length];
        for(int i = 0; i < array.length; i++)
            bundles[i] = new StringBundle(nagger, array[i]);
        return bundles;
    }

    /**
     * Parses an array of Strings into an array of StringBundles<br/>
     * 
     *
     * @param nagger the Nagger to report errors to
     * @param array the array of Strings to parse
     * @return the newly created array, containing parsed Strings
     */
    public static StringBundle[] parse(Nagger nagger, String... array) {
        StringBundle[] result = new StringBundle[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = parse(nagger, array[i]);
        return result;
    }

    /**
     * Parses a String into a StringBundle, this means:<br/>
     * Placeholders, unicode escapes and boxes (\x)<br/>
     * 
     *
     * @param nagger the nagger to report errors to
     * @param str the String to parse
     * @return a parsed String, as StringBundle
     */
    public static StringBundle parse(Nagger nagger, String str) {
        if (str == null) return null;

        StringBundle bundle = new StringBundle(nagger);
        final StringBuilder builder = new StringBuilder(str.length());

        StringReader reader = new StringReader(str);
        boolean escape = false;
        while (reader.isReadable()) {
            char c = reader.readChar();
            if (c == '\\') {
                if (!(escape = !escape)) // Escaped back-slash
                    builder.append(c);
            } else if (escape) {
                escape = false;
                switch (c) {
                    case 'u': // Unicode character
                        try {
                            builder.append((char) Integer.parseInt(reader.readString(4), 16));
                        } catch (StringIndexOutOfBoundsException ex) {
                            nagger.nag("Error on parsing unicode character in string " + str + ": "
                                    + "Expected number to have 4 digits!");
                        } catch (NumberFormatException ex) {
                            nagger.nag("Error on parsing unicode character in string " + str + ": "
                                    + "Invalid characters (Allowed: 0-9 and A-F)!");
                        }
                        break;
                    case '%': //Escaped placeholder character
                        builder.append(c);
                        break;
                    case 'x': case 'X':
                        builder.append(BOX);
                        break;
                    case '(':
                        if (builder.length() > 0) {
                            bundle.add(builder.toString());
                            builder.setLength(0);
                        }
                        char read = reader.readChar();
                        NumberFormat format;
                        if (read == ':') {
                            while ((read = reader.readChar()) != ':')
                                builder.append(read);
                            format = new DecimalFormat(builder.toString(), Formula.getSymbols());
                            builder.setLength(0);
                        } else {
                            reader.unreadChar();
                            format = null;
                        }
                        try {
                            bundle.add(Formula.parse(reader, ParsingContext.ofFormat(format), true));
                        } catch (IllegalArgumentException ex) {
                            nagger.nag("Failed to parse formula in string " + str + ": " + ex.getMessage());
                        }
                        break;
                }
            } else switch(c) {
                case '%': //Start of placeholder
                    if (!reader.isReadable())
                        nagger.nag("PapiPlaceholder " + builder + " ends with a placeholder symbol!");
                    if (reader.isReadable() && reader.peekChar() == '%') { // Double-character escape
                        reader.skipChar();
                        builder.append(c);
                        break;
                    }
                    if (builder.length() > 0) {
                        bundle.add(builder.toString());
                        builder.setLength(0);
                    }
                    while ((c = reader.readChar()) != '%') {
                        if (!reader.isReadable())
                            nagger.nag("PapiPlaceholder "
                                    + builder + " wasn't closed off! Was it a mistake or "
                                    + "did you forget to escape the '%' symbol?");
                        builder.append(c);
                    }
                    bundle.add(adapter.apply(builder.toString()));
                    builder.setLength(0);
                    break;
                default:
                    builder.append(c);
                    break;
            }
        }
        if (builder.length() > 0) bundle.add(builder.toString());
        return bundle;
    }

    @Override
    public StringBundle clone() {
        return new StringBundle(nagger, this);
    }
}
