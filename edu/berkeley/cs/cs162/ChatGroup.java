package edu.berkeley.cs.cs162;

import java.sql.SQLException;
import java.util.Vector;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ChatGroup implements ChatEndpoint {
	private String m_name;
	private Vector<BaseUser> m_members;
	public int gid;
	private ChatServer server;

	ChatGroup(String name,ChatServer s) {
		setName(name);
		m_members = new Vector<BaseUser>();
		server=s;
	}

	public synchronized boolean addUser(BaseUser user) {
		if (!m_members.contains(user)) {
			m_members.add(user);
			return true;
		} else {
			return false;
		}
	}

	public synchronized boolean delUser(BaseUser user) {
		return m_members.remove(user);
	}

	public synchronized boolean containsUser(BaseUser user) {

		return m_members.contains(user);
	}

	/**
	 * Deliver a message to the group.
	 * 
	 * @message the message to send
	 * @return true if it was successfully sent to all members
	 */
	public synchronized boolean deliverMessage(Message message) {
		boolean rv = true;
		/*
		 * deliver to the output queues of all the group members
		 * 
		 * synchronization: called within the monitor
		 */
		//message.groupid=this.gid;
		for (BaseUser c : m_members) {
			ChatUser cu = (ChatUser)c;
			if(cu.isActive()){
				try {
					System.out.println("sending to: " + cu.getUserName());
					cu.write(message);
				} catch (Exception e){
					e.printStackTrace();
				}
				//rv = c.deliverMessage(message) && rv;
			} else {
				//rv = false;
				try {
					server.db.sendOfflineGroupMessage(cu.userid, this.gid, message.getSrc(), message.getMessage());
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
		return rv;
	}

	public void setName(String m_name) {
		this.m_name = m_name;
	}

	public String getName() {
		return m_name;
	}
}
