package edu.berkeley.cs.cs162;

public abstract class BaseUser extends Thread implements ChatEndpoint {

	public int userid;
	public BaseUser() {
		super();
	}

	public void connected() {
		this.start();
	}

	public void send(BaseUser bu, String msg) {
	
	}
	public void msgReceived(String msg){
		
	}

}
