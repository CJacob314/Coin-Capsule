/* Copyright (C) Jacob Cohen - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Jacob Cohen <jcohen30@uic.edu> or <jacob@jacobcohen.info>
 */
package com.cjacob314.apps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Jacob Cohen <jcohen30@uic.edu> or <jacob@jacobcohen.info>
 */
public class JLogger {
	private static final String logDirName = "logs"; // will go one out of the java-webserv folder
	private static final LocalDateTime start = AppMain.appStart;
	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss");
	private static final DateTimeFormatter dtfShort = DateTimeFormatter.ofPattern("HH-mm-ss");
	private static final String startString = start.format(dtf);


	private static FileOutputStream logOut;
	private static boolean lineOne = true;

	static{
		try {
			new File("../" + logDirName).mkdirs();
			File newOut = new File("../" + logDirName + "/log" + startString + ".log");
			newOut.createNewFile();
			logOut = new FileOutputStream(newOut, true);
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	public static void log (String toLog) {
		String toPrint = lineOne ? "" : "\n" + (LocalDateTime.now().format(dtfShort) + "[LOG] " + toLog);
		System.out.println(toPrint.replaceFirst("\n", ""));
		try{
			logOut.write(toPrint.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e){
			e.printStackTrace();
		}
		lineOne = false;
	}
}
