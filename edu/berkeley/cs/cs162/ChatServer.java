package edu.berkeley.cs.cs162;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.net.*;

import edu.berkeley.cs.cs162.test.TestChatServer;

/**
 * Implement shared chat server functionality -- track active users and groups,
 * deliver messages to output queues.
 * 
 * @author Stephen Dawson-Haggerty <stevedh@eecs.berkeley.edu>
 * @author Jorge Ortiz <jortiz@eecs.berkeley.edu>
 * 
 */

public class ChatServer implements ChatServerInterface {
	static final int MAX_ACTIVE_USERS = 100;
	static final int MAX_PENDING_USERS = 10;
	private ChatAuthenticator m_authenticator;
	private BlockingQueue<ChatUser> m_pending_users;
	private ConcurrentHashMap<String, ChatUser> m_active_users;
	private ConcurrentHashMap<String, ChatGroup> m_active_groups;
	private boolean m_shutting_down;
	public ChatServerDataBase db;

	private static int sockPort = 4747;
	private static ServerSocket serverSock = null;
	private static ChatServerIdleConns idleConnsMngr = null;
	

	public ChatServer() {
		m_active_users = new ConcurrentHashMap<String, ChatUser>();
		m_active_groups = new ConcurrentHashMap<String, ChatGroup>();
		m_pending_users = new ArrayBlockingQueue<ChatUser>(MAX_PENDING_USERS);
		m_authenticator = new ChatAuthenticator();
		m_authenticator.server=this;
		m_shutting_down = false;
		
		db= new ChatServerDataBase();
		try {
			db.connect("ec2-50-17-180-71.compute-1.amazonaws.com", "group05", "group05", "CynozB61");
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		//set up the server socket	
		try {	
			if(serverSock == null)
				serverSock = new ServerSocket(sockPort);
		} catch(Exception e){
			e.printStackTrace();
		}

		//start up the IdleSocket
		idleConnsMngr = ChatServerIdleConns.getInstance(this);
	}

	public static void main(String[] args){
		ChatServer thisChatServer = new ChatServer();
		thisChatServer.start();	
	}

	public void start(){
		try {
			Socket s = null;
			while((s=serverSock.accept()) != null){
				try {
					idleConnsMngr.addSocket(s);
					//InputStream is = s.getInputStream();
					//ObjectInputStream objIS = new ObjectInputStream(is);
					//Message m = (Message) objIS.readObject();
					//handleMessage(s, objIS, m);
				} catch (Exception oe){
					oe.printStackTrace();
					//log dropped message
					//send error back to user
				}
			}
		} catch(Exception e){
			e.printStackTrace();
		}
		return;
	}


	/**
	 *  Handles the message received from a client.
	 * 
	 * @param s newly created socket connection accepted by the server
	 * @param ois the ObjectInputStream associated with this socket.  Used to read Message objects from the
	 * 		input stream.
	 * @param oos the ObjectOoutputStream assocaited with this socket.  Used to write Message object to the
	 * 		output stream.
	 * @param m message read from the socket's ObjectInputStream.
	 */
	public boolean handleMessage(Socket s, ObjectInputStream ois, ObjectOutputStream oos, Message m){

		try {
			Message resp = null;
			switch(m.getType()){
				case CONNECT:
					//System.out.println("Connect message rcvd");
					handleConnectOnly(s);
					return true;
				case DISCONNECT:
					//send disconnect ok
					resp = new Message("ChatServer", m.getSrc(), "disconnect OK", m.getSqn(), MessageType.DISCONNECT_OK);
					oos.writeObject(resp);
					oos.flush();
					s.close();
					return true;

				
				case ADD_USER:
					boolean worked=false;
					try {
						db.addUser(m.getSrc(), m.getMessage());
						worked=true;
						resp = new Message("ChatServer", m.getSrc(), "adduser OK", m.getSqn(), MessageType.ADD_USER_OK);
					} catch (SQLException e) {

						resp = new Message("ChatServer", m.getSrc(), "adduser REJECTED", m.getSqn(), MessageType.ADD_USER_REJECTED);
					}
					
					oos.writeObject(resp);
					oos.flush();
					
					return worked;
				case LOGIN:
					//if the chatserver is not full, and username is unique, send login ok
					//if the chatserver is full, but put on wait queue, send queued
					//else send rejected and close socket
					LoginError loginError = login(m.getSrc(), m.getMessage(), s, ois, oos);
					ChatUser thisUser = getUser(m.getSrc());
					if(loginError == LoginError.USER_ACCEPTED){
						//System.out.println("send login ok: " + m.toString());
						//send connect ok
						resp = new Message("ChatServer", m.getSrc(), "login OK", m.getSqn(), MessageType.LOGIN_OK);
						thisUser.write(resp);
						return true;
					} else if(loginError == LoginError.USER_QUEUED){
						resp = new Message("ChatServer", m.getSrc(), "login QUEUED", m.getSqn(), MessageType.LOGIN_QUEUED);
						oos.writeObject(resp);
						oos.flush();
						return false;
					} else if(loginError == LoginError.USER_DROPPED || loginError == LoginError.USER_REJECTED){
						resp = new Message("ChatServer", m.getSrc(), "login REJECTED", m.getSqn(), MessageType.LOGIN_REJECTED);
						oos.writeObject(resp);
						oos.flush();
						TestChatServer.logUserLoginFailed(m.getSrc(), new Date(), loginError);
						return false;
					}
					
					break;
				default:
					//send unknown message response
					resp = new Message("ChatServer", m.getSrc(), "INVALID STATE: not logged in", m.getSqn(), MessageType.INVALID_STATE);
					oos.writeObject(resp);
					oos.flush();
					s.close();
					TestChatServer.logChatServerDropMsg(m.toString(), new Date());
					return false;
			}
		} catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}

	/**
	 *
	 */
	public void handleConnectOnly(Socket s){
		idleConnsMngr.addSocket(s);
	}

	/**
	 *
	 */
	public void handleConnectOnly(Socket s, ObjectInputStream ois, ObjectOutputStream oos){
		idleConnsMngr.addSocket(s, ois, oos);
	}

	/**
	 *
	 */
	public ChatGroup getGroup(String name) {
		return m_active_groups.get(name);
	}

	/**
	 *
	 */
	public ChatUser getUser(String name) {
		return m_active_users.get(name);
	}

	/**
	 *
	 */
	public String getUserName(BaseUser user) {
		synchronized (m_active_users) {
			for (String thisUsername : m_active_users.keySet()) {
				BaseUser thisBaseUser = m_active_users.get(thisUsername);
				if (thisBaseUser == user) {
					return thisUsername;
				}
			}
			return null;
		}
	}

	/**
	 * Log a user into the server
	 * 
	 * @param user a new username
	 * @param credentials login credentials
	 * @param socket the socket associated with the connection to the client
	 * @param ois ObjectInputStream the input stream associated with this socket.  Used to read 
	 *		Message objects from the client.
	 * @param oos ObjectOutputStream the output stream assocaited with this socket.  Used to write
	 * 		Message objects to the client.
	 * @return one of the results specified in the specification
	 */
	public LoginError login(String user, String credentials, Socket socket,
			ObjectInputStream ois, ObjectOutputStream oos) {
		/*
		 * make sure the active users table doesn't mutate while we're looking
		 * at it, since we need to do a Read-Modify-Write
		 */
		synchronized (m_active_users) {
			/* don't accept new users during a shutdown */
			if (m_shutting_down) {
				return LoginError.USER_DROPPED;
			}

			/* no authenticate no login */
			int uid=m_authenticator.authenticate(user, credentials);
			if (!m_active_users.containsKey(user)  && (uid>0)) {
				ChatUser newUser = new ChatUser(user, socket, ois, oos);
				newUser.userid=uid;
				/* login immediately */
				if (m_active_users.size() < MAX_ACTIVE_USERS) {
					/* do an immediate login and tell the user she's in */
					m_active_users.put(user, newUser);
					newUser.setState(UserState.LOGGED_IN);
					newUser.connected(this);
					TestChatServer.logUserLogin(user, new Date());

					return LoginError.USER_ACCEPTED;
				} else {
					/* this won't block */
					if (m_pending_users.offer(newUser)) {
						/* but the wait queue might be full */
						return LoginError.USER_QUEUED;
					} else {
						TestChatServer.logUserLoginFailed(user, new Date(),
								LoginError.USER_DROPPED);
						return LoginError.USER_DROPPED;
					}
				}
			} else {
				TestChatServer.logUserLoginFailed(user, new Date(),
						LoginError.USER_REJECTED);
				return LoginError.USER_REJECTED;
			}
		}
	}

	/**
	 * Log a user out.
	 * 
	 * @param the
	 *            username to logff
	 * @return false if the user was not logged in, true otherwise.
	 */
	public boolean logoff(String username) {
		/*
		 * remove the user from the map so we can't send messages to her
		 * synchronization: not needed since it's a ConcurrentHashMap
		 */
		ChatUser user = m_active_users.remove(username);
		if (user == null) {
			return false;
		}

		TestChatServer.logUserLogout(username, new Date());

		/*
		 * remove the user from any groups she was in
		 * 
		 * synchronization: we must make sure that the group map doesn't mutate
		 * while we're looking at it.
		 */
		synchronized (m_active_groups) {
			for (ChatGroup g : m_active_groups.values()) {
				g.delUser(user);
			}
		}
		user.setState(UserState.CONNECTED);

		/*
		 * now that we're totally logged off, we can see if there were pending
		 * logins that we need to process
		 * 
		 * synchronization: use m_active users so we don't conflict with
		 * concurrent login requests.
		 */
		synchronized (m_active_users) {
			user = m_pending_users.poll();
			if (user == null)
				return true;
			if(!m_active_users.containsKey(user.getName())){
				m_active_users.put(user.getName(), user);
				user.setState(UserState.LOGGED_IN);
			}
		}

		/* signal that the user is now connected for deferred joins */
		user.setState(UserState.LOGGED_IN);
		user.connected(this);
		TestChatServer.logUserLogin(user.getName(), new Date());
		return true;
	}

	private ChatGroup createGroup(String groupname) {
		synchronized (m_active_groups) {
			ChatGroup newcg = new ChatGroup(groupname,this);
			try {
				newcg.gid=db.createGroup(groupname);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			m_active_groups.putIfAbsent(groupname, newcg);
			return newcg;
		}
	}

	/**
	 * Request to join a chat group. The user must be logged in.
	 * 
	 * @param user
	 *            requesting user
	 * @param groupname
	 *            the group requested to join
	 * @return true if the group was successfully joined
	 */
	public boolean joinGroup(BaseUser user, String groupname) {
		if (user == null || groupname == null) {
			return false;
		}
		/*
		 * get the requested group and add the user if she is not already a
		 * member
		 * 
		 * synchronization: on the active_groups in case leaveGroup is called
		 * concurrently.
		 */
		synchronized (m_active_groups) {
			ChatGroup requested_group = m_active_groups.get(groupname);
			if (requested_group == null) {
				requested_group = createGroup(groupname);
			}

			if (requested_group.containsUser(user)) {
				return false;
			} else {
				requested_group.addUser(user);
				try {
					db.joinGroup(user.userid, requested_group.gid);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				TestChatServer.logUserJoinGroup(groupname, getUserName(user),
						new Date());
				return true;
			}
		}
	}

	public boolean groupExists(String groupname){
		ChatGroup requested_group = m_active_groups.get(groupname);
		if (requested_group == null) 
			return false;
		return true;
	}

	public boolean amGroupMember(BaseUser user, String groupname){
		ChatGroup requested_group = m_active_groups.get(groupname);
		if (requested_group == null) 
			return false;
		requested_group.containsUser(user);
		return true;
	}

	/**
	 * Request to leave a chat group.
	 * 
	 * @param user
	 *            the user to remove
	 * @param groupname
	 *            the group to remove the user from
	 * @return true if the user was successfully removed from the group
	 */
	public boolean leaveGroup(BaseUser user, String groupname) {
		boolean groupLeaveSuccess = false;
		synchronized (m_active_groups) {
			ChatGroup g = m_active_groups.get(groupname);
			if (g == null)
				return false;
			groupLeaveSuccess = g.delUser(user);
			if (groupLeaveSuccess){
				try {
					db.leaveGroup(user.userid, g.gid);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				TestChatServer.logUserLeaveGroup(groupname, getUserName(user),
						new Date());
			}
		}
		return groupLeaveSuccess;
	}


	/**
	 * Deliver a message to an output queue based on the addressing information
	 * in the message
	 * 
	 * @param m
	 *            das message
	 * @return false if the destination couldn't be found; otherwise the result
	 *         of attempting to enqueue the message.
	 */
	public boolean sendUnicastMessage(Message m) {
		//should check if the user is in the group
		ChatEndpoint e;
		e = m_active_users.get(m.getDest());
		if (e == null) {
			try {
				db.sendOfflineMessage(m);
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return false;
		}

		return e.deliverMessage(m);
	}

	/**
	 * Deliver a message to an output queue based on the addressing information
	 * in the message
	 * 
	 * @param m
	 *            das message
	 * @return false if the destination couldn't be found; otherwise the result
	 *         of attempting to enqueue the message.
	 */
	public boolean sendGroupMessage(Message m) {
		//should check if the user is in the group
		ChatEndpoint e;
		boolean isMember = false;
		e = m_active_groups.get(m.getDest());
		if (e != null) {
			isMember = ((ChatGroup)e).containsUser(m_active_users.get(m.getSrc()));
			if(!isMember)
				return false;
		} else{
			return false;
		}

		//loop through all sockets for each user in the group to deliver the message
		return e.deliverMessage(m);
	}

	/**
	 * Shutdown the chat server by sending signals to all threads and then
	 * waiting for them to exit. This will wait for them to finish delivering
	 * messages and the threads to exit cleanly.
	 * 
	 * @throws InterruptedException
	 *             if we get interrupted waiting for threads to exit
	 */
	public void shutdown() throws InterruptedException {
		Collection<ChatUser> shutdownSet;
		System.err.println("Shutting down");

		/* let everybody know we're going down */
		synchronized (m_active_users) {
			m_shutting_down = true;
			/*
			 * make a copy of the current user set, because shutting down may
			 * mutate the user set but we don't want to have it locked. setting
			 * this flag will prevent us from creating new users during
			 * shutdown.
			 */
			shutdownSet = new ArrayList<ChatUser>(m_active_users.values());
		}

		/* initiate an orderly shutdown */
		for (ChatUser u : shutdownSet) {
			u.shutdown();
		}

		/* and then wait for all threads to exit */
		for (ChatUser u : shutdownSet) {
			u.join();
		}
	}
}
