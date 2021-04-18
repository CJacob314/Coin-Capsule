package com.cjacob314.apps;

import java.time.LocalDateTime;
import java.util.*;

public class AppMain
{
    public static final LocalDateTime appStart = LocalDateTime.now();

    public static void main(String[] args)
    {
        Calendar.getInstance();
        JLogger.log("WebServer started...");
        Server.getServer();
    }
}
