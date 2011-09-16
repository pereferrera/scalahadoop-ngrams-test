package com.datasalt.scala;

import org.apache.hadoop.util.ProgramDriver;
import com.datasalt.scala.BoomingNGramsJob;

public class Driver extends ProgramDriver {

	public Driver() throws Throwable {
		super();
		addClass("ngramstatsjob", BoomingNGramsJob.class, "This Job is a small experiment that uses ScalaHadoop to crunch the Google N-Grams, trying to find words whose importance boomed significantly in a period of 5 or less years.");
	}
	
	public static void main(String[] args) throws Throwable {
		Driver driver = new Driver();
		driver.driver(args);
		System.exit(0);
	}
}
