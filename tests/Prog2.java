//class Prog2{
//	
//	public static void main(String []args){
//		
//		f0(7);
//		for(int i=0;i<10;i++)
//		{
//			if(i<=5) {
//				f1();
//			}
//			if(i == 8) {
//				f2();
//			}
//		}
//		
//		f3();
//		
//		
//		f0(4);
//		f1();
//		
//	}	
//	
//	public static void f0(int x) {
//		int a = 5;
//		if(x != 5) f1();
//		else f2();
//	}
//	
//	public static void f3() {
//		int a= 6;
//	}
//	
//	public static void f1() {
//		int a= 7;
//		f2();
//		f3();
//	}
//	
//	public static void f2() {
//		int a= 8;
////		System.out.println("f2");
//	}
//	
//}
//

class Prog2{
	
	public static void main(String []args){
		
		B o1 = new B();
		
		int n1 = 5;
		
		if(n1>5)
		{
			f0(o1);
		}else {
			f3(o1);
		}
	}	
	
	public static void f0(B o1) {
		int a= 5;
		o1.f1 = 5;
//		System.out.println("f0");
		
	}
	
	public static void f3(B o1) {
		int a= 6;
//		System.out.println("f3");
	}
	
	public static void f1(B o1) {
		int a= 7;
//		System.out.println("f1");
	}
	
	public static void f2(B o1) {
		int a= 8;
//		System.out.println("f2");
	}
}

class B{
	
	int f1;
}
