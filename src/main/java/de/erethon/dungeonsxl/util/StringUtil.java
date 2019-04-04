package de.erethon.dungeonsxl.util;

import java.time.Duration;
import java.util.List;

public class StringUtil {

    public static String concatList(List<String> list) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            final boolean last = list.size() - 1 == i;
            builder.append(list.get(0));
            if(!last) builder.append(", ");
        }
        return builder.toString();
    }

    public static String humanReadableMillis(long time) {
        return Duration.ofMillis(time).toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }
}