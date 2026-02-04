package tatar.eljah.util;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

public class TextDiffUtil {

    public static Spannable highlightDiff(String expected, String actual) {
        if (actual == null) {
            actual = "";
        }
        if (expected == null) {
            expected = "";
        }

        SpannableString spannable = new SpannableString(actual);
        int len = Math.max(expected.length(), actual.length());

        for (int i = 0; i < len; i++) {
            char exp = i < expected.length() ? expected.charAt(i) : 0;
            char act = i < actual.length() ? actual.charAt(i) : 0;
            if (exp != act && i < actual.length()) {
                spannable.setSpan(
                        new ForegroundColorSpan(Color.RED),
                        i,
                        i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
        return spannable;
    }
}
