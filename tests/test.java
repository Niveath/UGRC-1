class test {
	public static void main(String args[]) {
		A a1 = new A(); // obj 1
		
		f1(a1);
		
		f2(a1);
	}
	
	public static void f1(A a) {
		B b1 = new B(); // obj 20
		a.f = b1;
	}
	
	public static void f2(A a) {
		B b1 = a.f;
	}
}

class A {
	B f;
}

class B {}