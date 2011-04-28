package edu.berkeley.cs.cs162;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Date;
import java.net.Socket;
import java.net.SocketException;
import java.io.*;


import edu.berkeley.cs.cs162.test.TestChatServer;

/**
 * @author Jorge Ortiz <jortiz@eecs.berkeley.edu>
 * 
 */


public class ChatServerIdleConns {

	private static ConcurrentHashMap<Socket, TimerTask> m_connected_sockets;
	private static ConcurrentHashMap<Socket, ChannelBundle> m_socket_channel_bundles;
	private static PollTask m_poller = null;
	private static Timer m_pollTimer = null;
	private static ChatServer m_server = null;

	private static ChatServerIdleConns chatServerIdleConns = null;

	private ChatServerIdleConns(ChatServer server){
		try{
			m_poller = new PollTask(this);
			m_pollTimer = new Timer();
			m_pollTimer.schedule(m_poller, 1000, 1000);
			m_server = server;
			m_connected_sockets = new ConcurrentHashMap<Socket, TimerTask>();
			m_socket_channel_bundles = new ConcurrentHashMap<Socket, ChannelBundle>();
		} catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static ChatServerIdleConns getInstance(ChatServer server){
		if(chatServerIdleConns == null){
			chatServerIdleConns = new ChatServerIdleConns(server);
		}
		return chatServerIdleConns;
	}

	public synchronized boolean addSocket(Socket s){
		try {
			if(m_connected_sockets.containsKey(s))
				return false;
			Timer timer = new Timer();
			EvictTask etask = new EvictTask(s,this);
			ChannelBundle cbundle = new ChannelBundle(s);
		
			m_socket_channel_bundles.put(s, cbundle);
			m_connected_sockets.put(s, etask);

			timer.schedule(etask, 20*1000);
			return true;
		} catch(Exception e){
			return false;
		}
	}

	public synchronized boolean addSocket(Socket s, ObjectInputStream ois, ObjectOutputStream oos){
		try {
			System.out.println("adding new socket");
			if(m_connected_sockets.containsKey(s))
				return false;
			Timer timer = new Timer();
			EvictTask etask = new EvictTask(s,this);
			ChannelBundle cbundle = new ChannelBundle(s, ois, oos);
		
			m_socket_channel_bundles.put(s, cbundle);
			m_connected_sockets.put(s, etask);

			timer.schedule(etask, 20*1000);
			return true;
		} catch(Exception e){
			return false;
		}
	}

	public static boolean removeSocket(Socket s){
		try {
			m_connected_sockets.remove(s);
			return true;
		} catch(Exception e){
			return false;
		}
	}

	private class EvictTask extends TimerTask{

		private ChatServerIdleConns master = null;
		private Socket m_socket = null;

		public EvictTask(Socket s, ChatServerIdleConns m) throws Exception{
			if(s == null)
				throw new Exception("null socket");
			if ( m== null)
				throw new Exception("null ChatServerIdleConns");
			if(master == null)
				master = m;
			
			m_socket = s;
		}

		public void run(){
			master.removeSocket(m_socket);
			if(m_socket.isConnected()){
				try {
					System.out.println("Closing idle socket");
					//m_socket.close();
					ChannelBundle cbundle = m_socket_channel_bundles.get(m_socket);
					ObjectOutputStream oos = cbundle.getOOS();
					Message closingIdleMsg = new Message("ChatServer", null, "Closing idle socket", -1, 
							MessageType.DISCONNECT_OK);
					oos.writeObject(closingIdleMsg);
					oos.flush();
					cbundle.closeChannels();
					m_socket_channel_bundles.remove(m_socket);
				} catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	}

	private class PollTask extends TimerTask{
		private ChatServerIdleConns master = null;

		public PollTask(ChatServerIdleConns m) throws Exception{
			if ( m== null)
				throw new Exception("null ChatServerIdleConns");
			if(master == null)
				master = m;
		}

		public void run(){
			Message thisMsg=null;
			for (Socket thisSock : m_connected_sockets.keySet()) {
				try {
					ChannelBundle cbundle = m_socket_channel_bundles.get(thisSock);
					if(thisSock != null && thisSock.isConnected() && thisSock.getInputStream().available()>0 && cbundle!=null){
						//System.out.println("polltask: activity heard");
						ObjectInputStream ois = cbundle.getOIS();
						thisMsg = (Message) ois.readObject();
						MessageType mtype = thisMsg.getType();
						if(mtype == MessageType.DISCONNECT || mtype == MessageType.LOGIN || mtype == MessageType.ADD_USER ){
							//System.out.println("polltask:: thisSock: " + thisSock);
							if(m_server.handleMessage(thisSock, ois, cbundle.getOOS(), thisMsg)){
								//cancel the eviction task
								TimerTask thisTask = m_connected_sockets.get(thisSock);
								thisTask.cancel();
								m_connected_sockets.remove(thisSock);
							}
						}
					}
				} catch(Exception e){
					//e.printStackTrace();
					if(e instanceof SocketException){
						TimerTask thisTask = m_connected_sockets.get(thisSock);
						thisTask.cancel();
						m_connected_sockets.remove(thisSock);
					}
					if(thisMsg !=null)
						TestChatServer.logChatServerDropMsg(thisMsg.toString(), new Date());
				}
			}	
		}
	}

	private class ChannelBundle{
		private Socket m_socket;
		private ObjectInputStream m_ois;
		private ObjectOutputStream m_oos;

		public ChannelBundle(Socket s, ObjectInputStream ois, ObjectOutputStream oos){
			m_socket = s;
			m_ois = ois;
			m_oos = oos;
		}

		public ChannelBundle(Socket s){
			try{
				m_socket = s;
				m_ois = new ObjectInputStream(s.getInputStream());
				m_oos = new ObjectOutputStream(s.getOutputStream());
			}catch(Exception e){
				e.printStackTrace();
			}
		}

		public void closeChannels(){
			try{
				m_ois.close();
				m_oos.close();
				m_socket.close();
			} catch(Exception e){
				e.printStackTrace();
			}
		}

		public ObjectInputStream getOIS(){
			return m_ois;
		}
		
		public ObjectOutputStream getOOS(){
			return m_oos;
		}

		public Socket getSocket(){
			return m_socket;
		}

	}
}
