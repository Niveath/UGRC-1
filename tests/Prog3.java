class Prog3 {

	public static void main(String []args){
		
		B o1 = new B();
		
		f3(o1);
		
//		o1.fn();
		
//		o1.fn(o1);
		
	
		B o2 = new B();
		
		B o3 = new B();
		
		o1.f1 = o2;
		
		f0(o2);
	}	
	
	public static void f0(B o1) {
		int a= 5;
//		o1.f1 = o1;
//		System.out.println("f0");
		
	}
	
	public static void f3(B o1) {
		int a= 6;
//		o1.f1 = a;
		
		B b1 = new B();
		
		B b2 = b1;
//		System.out.println("f3");
	}
	
	public static void f1(B o1) {
		int a= 7;
//		System.out.println("f1");
	}
	
	public static void f2(B o1) {
		int a= 8;
		
		B temp = new B();
//		System.out.println("f2");
	}
}

class B{
	B f1;
	
	void fn(B x) {}
}