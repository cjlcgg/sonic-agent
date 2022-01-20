package com.phoenix;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;

public class Test {
    public static void main(String[] args) {
        String tempImageName = Calendar.getInstance().getTimeInMillis() + ".jpg";
        Path path = Paths.get(System.getProperty("user.dir"), "test-output", tempImageName);
        System.out.println(path.toString());
    }
}
