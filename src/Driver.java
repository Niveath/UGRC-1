package ugrc1;

import java.util.Arrays;

import ugrc1.callGraphAnalysis2;

import soot.PackManager;
import soot.Transform;

public class Driver {
	static boolean debug = false;
	
	public static void main (String [] args) {

		String classPath = "tests";
		String mainClass = "test";
		if(args != null && args.length > 0) {
			int i = 0;
			while(true) {
				if(args[i].equals("-cp")) {
					classPath = args[i+1];
					i += 2;
				} else if (args[i].equals("-mainClass")) {
					mainClass = args[i + 1];
					i += 2;
				}
				
				if(i + 1 > args.length) break;
			}
		}
		
		String [] sootArgs = {
				"-v",
				"-cp", classPath,
				"-pp",
				"-w", "-app",
				"-src-prec", "c",
				"-p", "cg.cha", "enabled:false",
				"-p", "cg.spark", "enabled:true",
				"-f", "J",
				//"-process-dir", classPath,
				mainClass
				
		};
		
		if(debug) System.out.println("The soot arguments are " + Arrays.toString(sootArgs));
		
		callGraphAnalysis cga = new callGraphAnalysis();
		
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.cga", cga));
		
		soot.Main.main(sootArgs);
	}
}

