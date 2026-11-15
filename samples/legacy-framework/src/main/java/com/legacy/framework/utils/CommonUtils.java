package com.legacy.framework.utils;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Random;

public class CommonUtils {

    public static Page page;
    static final String baseUrl = "https://qa.shop.example.com";

    public static String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public static String formatDate(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
        String text = format.format(date);
        System.out.println("Formatted date: " + text);
        return text;
    }

    public static void waitFor(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String readProperty(String key) {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream("config.properties"));
            return props.getProperty(key);
        } catch (Exception e) {
            System.out.println("could not read " + key);
        }
        return null;
    }

    public static Browser launch(Playwright playwright, String name) {
        if (name.equals("chrome")) {
            return playwright.chromium().launch();
        } else if (name.equals("firefox")) {
            return playwright.firefox().launch();
        } else if (name.equals("webkit")) {
            return playwright.webkit().launch();
        }
        return null;
    }

    public static String joinNames(List<String> names) {
        String result = "";
        for (String n : names) {
            result += n + ",";
        }
        return result;
    }

    public static void method2(String status) {
        if (status.equals("ACTIVE")) {
            System.out.println("ACTIVE");
        }
        System.out.println("status was ACTIVE? " + status.equals("ACTIVE"));
    }
}
