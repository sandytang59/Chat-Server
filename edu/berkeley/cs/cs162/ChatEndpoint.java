package edu.berkeley.cs.cs162;

import java.net.Socket;

public interface ChatEndpoint {
	/**
	 * Deliver a message to the user or group
	 * @param src the sending user (messages are always sent from users)
	 * @param Message the message itself
	 * @return true if the message was accepted for delivery
	 */
	public boolean deliverMessage(Message message);
}
