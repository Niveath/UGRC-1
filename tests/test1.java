class test1{
	public static void main(String []args){
			B t1;
			C t2;
			Buf b;
			t1 = new B();
			t2 = new C();
			b = new Buf();
			t1.b = b;
			t2.b = b;

			t1.start();
			t2.start();
			t1.join();
			t2.join();
	}	
}

class B{
	Buf b;
	public void start() {
		this.start();
	}
	public void join() {this.start();}
}

class C{
	Buf b;
	public void start() {}
	public void join() {this.start();}
}

class Buf{}