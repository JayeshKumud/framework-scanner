package com.legacy.framework.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {

    // copied from CommonUtils
    public static String formatDate(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
        String text = format.format(date);
        System.out.println("Formatted date: " + text);   // debug
        return text;
    }
}
