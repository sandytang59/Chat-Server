package edu.berkeley.cs.cs162;

import edu.berkeley.cs.cs162.test.TestChatServer;

import java.sql.ResultSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.Date;
import java.net.Socket;
import java.io.*;

/**
 * ChatUser represents a connected,logged-in client on the ChatServer.
 * Please refer to the specifiction for CS162 Project 2 (Spring 2011) for a detailed specification.
 *
 * @author Jorge Ortiz (jortiz@cs.berkeley.edu)
 */
public class ChatUser extends BaseUser implements ChatEndpoint {
	static final int CAPACITY_LIMIT = 2000;
	private int m_seqno;
	private ChatServer m_server;
	/* my name ("Steve") */
	public String m_name;
	/* pending messages which I need to deliver */

	private Socket m_socket;
	private ObjectInputStream m_ois;
	private ObjectOutputStream m_oos;

	private UserState m_state = UserState.DISCONNECTED;
		
	public ChatUser(String name, Socket socket, ObjectInputStream ois, ObjectOutputStream oos) {
		this.setName(name);
		m_name = name;
		m_seqno = 0;
		m_socket = socket;
		m_ois = ois;
		m_oos = oos;
	}

	public synchronized void setState(UserState state){
		m_state = state;
	}

	public synchronized UserState getState(UserState state){
		return m_state;
	}

	private ObjectOutputStream getOOS(){
		return m_oos;
	}

	private ObjectInputStream getOIS(){
		return m_ois;
	}

	public void write(Message m){
		try {
			if(m_socket.isConnected()){
				m_oos.writeObject(m);
				m_oos.flush();
			}
		} catch(Exception e){
			//e.printStackTrace();
			TestChatServer.logChatServerDropMsg(m.toString(), new Date());
		}
	}

	public Message read(){
		try{
			if(m_socket.isConnected())
				return (Message) m_ois.readObject();
		} catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	public synchronized void closeChannels(){
		try{
			m_oos.close();
			m_ois.close();
			m_socket.close();
			m_state = UserState.DISCONNECTED;
		} catch(Exception e){
			e.printStackTrace();
		}
	}

	public boolean isActive(){
		return m_socket.isConnected();
	}

	/**
	 * Signaled when the user has been logged in and should start running
	 * @param server
	 */
	public void connected(ChatServer server) {
		m_server = server;
		System.err.println(m_name + " connnected");
		super.connected();
 	}
	
	public String getUserName() {
		return m_name;
	}

	/**
	 * Implement delivering the message to the user. In the future, this may
	 * mean sending it over the network; however at the moment we only need to
	 * deliver it to the console.
	 * 
	 * @param src
	 *            the individual originating the message
	 * @param message
	 *            the actual message
	 * @return
	 */
	public boolean deliverMessage(Message message) {
		String src = message.getSrc();
		String dst = message.getDest();
		ChatUser user = m_server.getUser(src);
		ChatUser dstUser = m_server.getUser(dst);
		if(dstUser != null) {
			if(dstUser.isActive() && message != null){
				try {
					dstUser.write(message);
				} catch (Exception e){
					e.printStackTrace();
					return false;
				}
				return true;
			} else if(!dstUser.isActive()){
				//cleaup up
				m_server.logoff(user.getUserName());
				return false;
			} 
		}

		return false;

	}
	
	/**
	 * Initiate an orderly shutdown of this thread.
	 */
	public void shutdown() {
	}

	public void run(){
		try {
			while(m_socket.isConnected()) {
				switch(m_state){
					case LOGGED_IN:
						//System.out.println("run logged in");
						try {
							Message m = (Message)m_ois.readObject();
							handleMessage(m);
						} catch(Exception internalException){
							//internalException.printStackTrace();
							//log some server error
							if(internalException instanceof EOFException){
								System.out.println(m_name + " disconnected");
								m_server.logoff(m_name);
								return;
							}
						}
						break;
					default:
						return;
				}
			}
		} catch(Exception runException){
			runException.printStackTrace();
			//send unexpected error and log the user out
		}
	}

	public void handleMessage(Message m){
		try {
			if(m != null && m.getSrc().equals(m_name)){
				switch(m.getType()){
					case DISCONNECT:
						Message resp=null;
						boolean logoff = false;
						//send disconnect ok
						//System.out.println("disconnect called");
						if(this.isActive() && (logoff=m_server.logoff(m_name))){
							resp = new Message("ChatServer", m.getSrc(), "disconnect OK", m.getSqn(), MessageType.DISCONNECT_OK);
							this.write(resp);
							this.closeChannels();
						} else if(!this.isActive() && logoff){
							resp = new Message("ChatServer", m.getSrc(), "disconnect NOT OK: could not log user off chat server", 
									m.getSqn(), MessageType.INVALID_STATE);
							this.write(resp);
						}
						break;
					case LOGOUT:
						resp=null;
						logoff = false;
						//System.out.println("handling logout request");
						//send disconnect ok
						if(this.isActive() && (logoff=m_server.logoff(m_name))){
							resp = new Message("ChatServer", m.getSrc(), "logout OK", m.getSqn(), MessageType.LOGOUT_OK);
							this.write(resp);
							synchronized(this){
								m_state = UserState.CONNECTED;
							}
							m_server.handleConnectOnly(m_socket, m_ois, m_oos);
							//add user to chatserver idle-watchlist
						} else if(this.isActive() && !logoff){
							resp = new Message("ChatServer", m.getSrc(), "logout NOT OK: could not logg user off chat server", 
									m.getSqn(), MessageType.INVALID_STATE);
							this.write(resp);
						}
						break;
					case JOIN_GROUP:
						resp = null;
						//call the chat server's join group
						boolean groupExists = m_server.groupExists(m.getGroupName());
						boolean joinGroupSuccess = false;
						if((joinGroupSuccess = m_server.joinGroup((BaseUser) this, m.getGroupName())) && groupExists && this.isActive()){
							resp = new Message("ChatServer", m.getSrc(), "join " + m.getGroupName() + " OK_JOIN", m.getSqn(), MessageType.JOIN_GROUP_OK_JOIN);
						} else if(this.isActive() && joinGroupSuccess && !groupExists){
							resp = new Message("ChatServer", m.getSrc(), "join " + m.getGroupName() + " OK_CREATE", m.getSqn(), MessageType.JOIN_GROUP_OK_CREATE);
						} else if(this.isActive() && !joinGroupSuccess && groupExists){
							resp = new Message("ChatServer", m.getSrc(), "join " + m.getGroupName() + " FAIL_FULL", m.getSqn(), MessageType.JOIN_GROUP_OK_CREATE);
						} else if (!this.isActive()){
							//kill this thread, remove the user and clean up
						}

						if(this.isActive() && resp !=null){
							this.write(resp);
						}
						break;
					case LEAVE_GROUP:
						//System.out.println("Leave group received: " + m.getGroupName());
						resp = null;
						//call the chat server's leave group
						groupExists = m_server.groupExists(m.getGroupName());
						//System.out.println("groupexists: " + groupExists);
						boolean amMember = m_server.amGroupMember(this, m.getGroupName());
						boolean leaveGroupSuccess = false;
						if((leaveGroupSuccess = m_server.leaveGroup((BaseUser) this, m.getGroupName())) && groupExists && this.isActive()){
							resp = new Message("ChatServer", m.getSrc(), "leave " + m.getGroupName() + " OK", 
									m.getSqn(), MessageType.LEAVE_GROUP_OK);
						} else if(this.isActive() && !groupExists){
							resp = new Message("ChatServer", m.getSrc(), "leave  " + m.getGroupName() + " BAD_GROUP", 
									m.getSqn(), MessageType.LEAVE_GROUP_BAD_GROUP);
						} else if(this.isActive() && !leaveGroupSuccess && groupExists && !amMember){
							resp = new Message("ChatServer", m.getSrc(), "leave " + m.getGroupName() + " NOT_MEMBER", 
									m.getSqn(), MessageType.LEAVE_GROUP_NOT_MEMBER);
						} else if (!this.isActive()){
							m_server.logoff(m_name);
							//kill this thread, remove the user and clean up
							synchronized(this){
								m_state = UserState.DISCONNECTED;
							}
						}
						//System.out.println("groupexists: " + groupExists + " leaveGroupSuccess: " + leaveGroupSuccess);

						if(this.isActive() && resp !=null){
							this.write(resp);
						}
						break;
					case SEND_MSG:
						//forward the call to the chat server to delivery the message
						resp = null;
						boolean dstIsUser = (m_server.getUser(m.getDest()) != null);
						boolean dstIsGroup = (m_server.getGroup(m.getDest()) != null);
						boolean validDest = (dstIsUser | dstIsGroup);
						boolean suc = false;
						//System.out.println("send_msg received");
						if (!validDest){
							resp = new Message("ChatServer", m.getSrc(), "send " + m.getSqn() + " BAD_DEST", 
									m.getSqn(), MessageType.SEND_MSG_BAD_DEST);
						} else if( dstIsUser && (suc=m_server.sendUnicastMessage(m)) && this.isActive()){
							resp = new Message("ChatServer", m.getSrc(), "send " + m.getSqn() + " OK", 
									m.getSqn(), MessageType.SEND_MSG_OK);
						} else if( dstIsGroup && (suc=m_server.sendGroupMessage(m)) && this.isActive()){
							resp = new Message("ChatServer", m.getSrc(), "send " + m.getSqn() + " OK", 
									m.getSqn(), MessageType.SEND_MSG_OK);
						} else if(dstIsUser && !suc && this.isActive()){
							resp = new Message("ChatServer", m.getSrc(), "send " + m.getSqn() + " FAIL", 
									m.getSqn(), MessageType.SEND_MSG_FAIL);
						} else if(dstIsGroup && !suc && this.isActive()){
							resp = new Message("ChatServer", m.getSrc(), "sendack " + m.getSqn() + " FAIL", 
									m.getSqn(), MessageType.SEND_ACK_FAILED);
						} else if(!this.isActive()){
							m_server.logoff(m_name);
							//kill this thread, remove the user and clean up
							synchronized(this){
								m_state = UserState.DISCONNECTED;
							}
						}

						//send the response if it's not null
						if(resp != null && this.isActive()){
							this.write(resp);
						}
						break;
					case READ_LOG:
						//ChatUser user = m_server.getUser(m.getSrc());
						ResultSet offlines=m_server.db.readUserLoggedMessages(userid);
						while (offlines.next()) {
							//Object[] r=db.getRow(offlines);//Date d=(Date) r[4];
							//Message msg=new Message(r[2].toString(), user.m_name, r[3].toString(), -1, MessageType.SEND_MSG);
							Message msg=new Message(offlines.getString("from"), m_name, offlines.getString("text"), -1, MessageType.SEND_MSG);
							msg.m_timestamp = offlines.getDate("timestamp").getTime()/1000L;
							deliverMessage(msg);
						}
						offlines.close();
						offlines=null;
						m_server.db.delUserLoggedMessages(userid);
						offlines=m_server.db.readGroupLoggedMessages(userid);
						while (offlines.next()) {
							Message msg=new Message(offlines.getString("from_user"), m_name, offlines.getString("text"), -1, MessageType.SEND_MSG,offlines.getString("groupname"));
							msg.m_timestamp = offlines.getDate("timestamp").getTime()/1000L;
							deliverMessage(msg);
						}
						m_server.db.delGroupLoggedMessages(userid);
						break;
					default:
						if(this.isActive()){
							//send unknown message response
							resp = new Message("ChatServer", m.getSrc(), "INVALID STATE: not logged in", 
									m.getSqn(), MessageType.INVALID_STATE);
							this.write(resp);
						} else {
							m_server.logoff(m_name);
							//kill this thread, remove the user and clean up
							synchronized(this){
								m_state = UserState.DISCONNECTED;
							}
						}

						break;
				}
			} else {
				if(this.isActive()){
					//send error to client
					Message resp = null;
					resp = new Message("ChatServer", m.getSrc(), "INVALID STATE: not logged in", m.getSqn(), MessageType.INVALID_STATE);
					this.write(resp);
				}
			}
		} catch(Exception handleMessageException){
			if(this.isActive()){
				//send error to client
				Message resp = null;
				resp = new Message("ChatServer", m.getSrc(), "INVALID STATE: generic error:"+handleMessageException.getMessage(), m.getSqn(), MessageType.INVALID_STATE);
				try {
					this.write(resp);
				} catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	}

}
