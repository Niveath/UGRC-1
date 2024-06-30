package com.test;

import java.util.Arrays;

import soot.PackManager;
import soot.Transform;
import soot.options.Options;

public class Driver {
	public static void main (String [] args) {

		String classPath = "tests";
		String mainClass = "Prog";
		
		String [] sootArgs = {
				"-v",
				"-cp", classPath,
				"-pp",
				"-w", "-app",
				"-src-prec", "c",
				"-p", "cg.cha", "enabled:false",
				"-p", "cg.spark", "enabled:true",
				//"-f", "J",
				//"-process-dir", classPath,
				mainClass
				
		};
		
		callGraphAnalysis pta = new callGraphAnalysis();
		System.out.println("created object of pta!!");
		
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.mhpA", pta));
		soot.Main.main(sootArgs);
		
	}
}
