package net.jejer.hipda.utils;

import android.text.TextUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Common utils
 * Created by GreenSkinMonster on 2015-03-23.
 */
public class Utils {

    private static Whitelist mWhitelist = null;

    private static String THIS_YEAR;
    private static String TODAY;
    private static String YESTERDAY;
    private static long UPDATE_TIME = 0;

    public static String nullToText(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        return text;
    }

    public static String trim(String text) {
        return nullToText(text).replace(String.valueOf((char) 160), " ").trim();
    }

    public static String shortyTime(String time) {
        if (TextUtils.isEmpty(time))
            return "";

        if (System.currentTimeMillis() - UPDATE_TIME > 10 * 60 * 1000 || THIS_YEAR == null) {
            SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy-M-d", Locale.US);
            SimpleDateFormat yearFormatter = new SimpleDateFormat("yyyy", Locale.US);
            Date now = new Date();
            THIS_YEAR = yearFormatter.format(now) + "-";
            TODAY = dayFormatter.format(now);
            YESTERDAY = dayFormatter.format(new Date(now.getTime() - 24 * 60 * 60 * 1000));
            UPDATE_TIME = System.currentTimeMillis();
        }

        if (time.contains(TODAY)) {
            time = time.replace(TODAY, "今天");
        } else if (time.contains(YESTERDAY)) {
            time = time.replace(YESTERDAY, "昨天");
        } else if (time.startsWith(THIS_YEAR)) {
            time = time.substring(THIS_YEAR.length());
        }
        return time;
    }

    /**
     * return parsable html for TextViewWithEmoticon
     */
    public static String clean(String html) {
        if (mWhitelist == null) {
            mWhitelist = new Whitelist();
            mWhitelist.addTags(
                    "a",
                    "br", "p",
                    "b", "i", "strike", "strong", "u",
                    "font")

                    .addAttributes("a", "href")
                    .addAttributes("font", "color")

                    .addProtocols("a", "href", "http", "https");
        }
        return Jsoup.clean(html, "", mWhitelist, new Document.OutputSettings().prettyPrint(false));
    }

}
