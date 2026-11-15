package com.legacy.framework.utils;

import java.util.Random;

public class StringHelper {

    /** Same as CommonUtils.generateRandomString, written again with other names. */
    public static String randomText(int size) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder builder = new StringBuilder();
        Random rnd = new Random();
        for (int idx = 0; idx < size; idx++) {
            builder.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    public static String temp(String s) {
        return s.trim().toLowerCase();
    }
}
